package io.tholath.demos.kafka;

import com.jcraft.jsch.*;
import java.io.*;
import java.net.*;
import java.util.Properties;

public class BackendService {

    private static final Properties config = new Properties();
    private static final String CONFIG_FILE = "application.properties";

    public static void main(String[] args) {
        // 1. SETUP: Load Configuration
        if (!loadConfiguration()) {
            return;
        }

        int port = Integer.parseInt(config.getProperty("app.port", "9999"));

        System.out.println("--------------------------------------------------");
        System.out.println("🚀  BLUE SHIELD SECURE BACKEND SERVICE STARTED");
        System.out.println("--------------------------------------------------");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("✅  TCP Listener Active on Port: " + port);
            System.out.println("⏳  Waiting for commands...");

            while (true) {
                Socket socket = serverSocket.accept();
                handleRequest(socket);
            }
        } catch (IOException ex) {
            System.err.println("❌  Server Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static boolean loadConfiguration() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            config.load(input);
            System.out.println("📄  Configuration loaded from " + CONFIG_FILE);
            return true;
        } catch (IOException ex) {
            System.err.println("❌  CRITICAL ERROR: Could not find '" + CONFIG_FILE + "'");
            return false;
        }
    }

    private static void handleRequest(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String command = reader.readLine();
            System.out.println("\n[INCOMING] Received Command: " + command);

            if (command != null && command.startsWith("PROCESS_FILE:")) {
                String fileName = command.split(":")[1].trim();
                performSecureTransfer(fileName);
            } else {
                System.out.println("⚠️  Unknown or empty command received.");
            }
        } catch (IOException e) {
            System.err.println("❌  Socket Read Error: " + e.getMessage());
        }
    }

    private static void performSecureTransfer(String filePath) {
        // 2. CONFIG: Read values
        String sftpHost = config.getProperty("sftp.host");
        String sftpUser = config.getProperty("sftp.user");
        String sftpPass = config.getProperty("sftp.pass");
        int sftpPort = Integer.parseInt(config.getProperty("sftp.port", "22"));
        String strictHostKey = config.getProperty("feature.strict_host_checking", "no");

        System.out.println("🔄  Initiating Secure Transfer for: " + filePath);

        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            // DNS Check
            InetAddress address = InetAddress.getByName(sftpHost);
            System.out.println("🔎  DNS Resolved: " + sftpHost + " -> " + address.getHostAddress());

            session = jsch.getSession(sftpUser, sftpHost, sftpPort);
            session.setPassword(sftpPass);

            Properties sshConfig = new Properties();
            sshConfig.put("StrictHostKeyChecking", strictHostKey);
            session.setConfig(sshConfig);

            System.out.println("🔐  Connecting to SFTP Server...");
            session.connect();
            System.out.println("✅  SSH Handshake Complete.");

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            // --- CRITICAL FIX: PATH HANDLING ---
            // 1. Verify the local file exists (e.g. "shared/patient_data.xml")
            File localFile = new File(filePath);
            if (!localFile.exists()) {
                System.err.println("❌  FILE ERROR: Could not find '" + filePath + "'");
                return;
            }

            // 2. Extract ONLY the filename for the destination (e.g. "patient_data.xml")
            String remoteFileName = localFile.getName();

            // 3. Upload: Read from full path, Write to "upload/filename"
            channelSftp.put(filePath, "upload/" + remoteFileName);

            System.out.println("📤  SUCCESS: File uploaded to " + sftpHost + ":/upload/" + remoteFileName);
            System.out.println("--------------------------------------------------");

        } catch (JSchException | SftpException | UnknownHostException e) {
            System.err.println("⛔  SFTP FAILED: " + e.getMessage());
        } finally {
            if (channelSftp != null) channelSftp.disconnect();
            if (session != null) session.disconnect();
        }
    }
}