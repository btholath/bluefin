using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;
using System.DirectoryServices.Protocols; // For LDAP
using System.Net;
using System.Net.Sockets; // For TcpClient

[ApiController]
[Route("api/[controller]")]
// FIX 1: Removed [RequireHttps] so it works in Docker HTTP
public class ClaimsController : ControllerBase
{
    private readonly ILogger<ClaimsController> _logger;
    private readonly IConfiguration _config;

    public ClaimsController(ILogger<ClaimsController> logger, IConfiguration config)
    {
        _logger = logger;
        _config = config;
    }

    [HttpPost("upload")]
        public async Task<IActionResult> UploadClaim(IFormFile file, [FromHeader] string username, [FromHeader] string password)
        {
            // 1. Authenticate
            if (!AuthenticateUserAD(username, password))
            {
                _logger.LogWarning($"Failed login attempt for {username}");
                return Unauthorized("Invalid credentials.");
            }

            if (file == null || file.Length == 0) return BadRequest("No file provided.");

            try
            {
                // 2. SAVE TO SHARED VOLUME (The Fix)
                string sharedFolder = Path.Combine(Directory.GetCurrentDirectory(), "shared");
                if (!Directory.Exists(sharedFolder)) Directory.CreateDirectory(sharedFolder);

                string filePath = Path.Combine(sharedFolder, file.FileName);

                using (var stream = new FileStream(filePath, FileMode.Create))
                {
                    await file.CopyToAsync(stream);
                }

                // This Log confirms the new code is running!
                _logger.LogInformation($"File saved to: {filePath}");

                // 3. Notify Java (Send the path relative to /app)
                await NotifyLegacyBackendAsync($"shared/{file.FileName}");
            }
            catch (Exception ex)
            {
                _logger.LogError($"Processing Error: {ex.Message}");
                return StatusCode(500, "Internal Server Error");
            }

            return Ok(new { Message = "File processed successfully" });
        }

    // REAL WORLD: LDAP/Active Directory Logic
    private bool AuthenticateUserAD(string user, string pass)
    {
        try
        {
            string adServer = _config["ActiveDirectory:Server"] ?? "corp-ad";
            int adPort = int.Parse(_config["ActiveDirectory:Port"] ?? "389");

            // FIX 2: Construct the Full DN (Distinguished Name)
            // OpenLDAP needs "cn=user,dc=..." to bind, not just "user"
            string userDn = $"cn={user},dc=healthprovider,dc=com";

            using (var connection = new LdapConnection(new LdapDirectoryIdentifier(adServer, adPort)))
            {
                connection.SessionOptions.ProtocolVersion = 3; // Standard LDAPv3
                connection.AuthType = AuthType.Basic;

                // Try to login with the specific user's DN and Password
                var credential = new NetworkCredential(userDn, pass);
                connection.Bind(credential);

                return true; // If Bind succeeds, password is correct
            }
        }
        catch (LdapException ex)
        {
            _logger.LogError($"LDAP Auth Failed: {ex.Message}");
            return false;
        }
    }

    // REAL WORLD: Internal TCP Integration
    private async Task NotifyLegacyBackendAsync(string filename)
    {
        // FIX 3: Use the Container Hostname from Config (Not 127.0.0.1)
        string backendHost = _config["JavaBackend:Host"] ?? "backend-proc";
        int backendPort = int.Parse(_config["JavaBackend:Port"] ?? "9999");

        using (var client = new TcpClient())
        {
            // Connect to the Java Container
            await client.ConnectAsync(backendHost, backendPort);

            using (var stream = client.GetStream())
            using (var writer = new StreamWriter(stream) { AutoFlush = true })
            {
                // Send the command
                await writer.WriteLineAsync($"PROCESS_FILE:{filename}");
            }
        }
    }
}