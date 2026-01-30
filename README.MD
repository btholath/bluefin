---

# 🛡️ Bluefin Security: Secure Hybrid Microservices Architecture

A proof-of-concept secure data ingestion pipeline that bridges modern **.NET 8** microservices with legacy **Java 21** systems. This project demonstrates polyglot orchestration, secure identity management (LDAP), and encrypted file transport (SFTP) within a self-healing Docker environment.

## 🚀 Project Overview

The system simulates a secure medical claim processing pipeline:

1. **Ingestion:** A **C# Web API** receives sensitive patient data via HTTP.
2. **Identity:** The user is authenticated against a live **OpenLDAP** directory.
3. **Handover:** Data is securely passed to a backend service via a **Shared Docker Volume**.
4. **Processing:** A **Java Backend** service (listening on TCP) picks up the file.
5. **Delivery:** The file is encrypted and uploaded to a partner bank's **Secure SFTP** server.

---

## 🏗️ Architecture

The system consists of 4 main services and 2 utility containers orchestrated via Docker Compose:

| Service | Technology | Port | Description |
| --- | --- | --- | --- |
| **Gateway** | .NET 8 (C#) | `5064` | Secure REST API, LDAP Auth, File Validation. |
| **Backend** | Java 21 | `—` | TCP Listener, SFTP Client (JSch), Batch Processor. |
| **Identity** | OpenLDAP | `1389` | Active Directory simulation for user authentication. |
| **Bank Vault** | Atmoz SFTP | `2222` | External partner simulation for secure file delivery. |
| **Utils** | Alpine Linux | `—` | Init containers for automated permission fixes (`chmod/chown`). |

---

## 🛠️ Tech Stack

* **Languages:** C# (ASP.NET Core 8), Java 21 (Maven)
* **Containerization:** Docker, Docker Compose
* **Security:** LDAP (Identity), SSH/SFTP (Transport), Non-root containers
* **Networking:** Internal Docker DNS, TCP Sockets

---

## ⚡ Getting Started

### Prerequisites

* **Docker Desktop** (Windows/Mac) or **Docker Engine** (Linux)
* **Git**

### Installation

1. **Clone the Repository**
```bash
git clone https://github.com/YOUR_USERNAME/bluefin.git
cd bluefin

```


2. **Start the Architecture**
   This command builds the images, creates the networks, and initializes the volumes.
```bash
docker-compose up -d --build

```


3. **Verify Status**
   Ensure all containers are running (except the `init` containers, which should be `Exited (0)`).
```bash
docker ps -a

```



---

## 🧪 Testing the Pipeline

To test the system, we simulate a doctor (`dr_smith`) uploading a claim file.

### 1. The Command

Run this command in PowerShell or Terminal:

```powershell
curl.exe -X POST "http://localhost:5064/api/claims/upload" `
     -H "username: dr_smith" `
     -H "password: DoctorPassword!" `
     -F "file=@patient_data.xml"

```

> **Note:** Ensure `patient_data.xml` exists in your current folder. A sample file is provided in the repo.

### 2. Expected Output

```json
{"message":"File processed successfully"}

```

### 3. Verification Logs

Check the logs to trace the request through the system.

**C# Gateway (Authentication & Handover):**

```bash
docker logs gateway-web
# Output:
# User dr_smith authenticated. Processing file: patient_data.xml
# File saved to: /app/shared/patient_data.xml

```

**Java Backend (Pickup & Delivery):**

```bash
docker logs backend-proc
# Output:
# [INCOMING] Received Command: PROCESS_FILE:shared/patient_data.xml
# 🔄 Initiating Secure Transfer...
# ✅ SSH Handshake Complete.
# 📤 SUCCESS: File uploaded to bank-sftp:/upload/patient_data.xml

```

**Bank Vault (Final Confirmation):**

```bash
docker exec bank-sftp ls -l /home/bank_user/upload
# Output:
# -rw-r--r-- 1 bank_user users 1089 ... patient_data.xml

```

---

## 🧩 Key Implementation Details

### The "Init Container" Pattern

One of the biggest challenges in Docker is managing Linux file permissions across containers. This project uses dedicated "Init Containers" to automatically set permissions on startup, ensuring the C# (App User) and Java (App User) containers can write to the same shared volume.

```yaml
# docker-compose.yml snippet
fix-permissions:
  image: alpine
  command: sh -c "mkdir -p /shared && chmod -R 777 /shared"
  volumes:
    - shared-data:/shared

```

### Polyglot Networking

Instead of using HTTP for everything, the C# Gateway communicates with the Java Backend via a raw **TCP Socket** on port 9999. This demonstrates high-performance, low-overhead inter-service communication common in legacy integrations.

---

## 🔧 Troubleshooting

**1. "Access Denied" or Permission Errors**
If the C# app crashes with `Access to the path ... is denied`, force a permission fix:

```bash
docker-compose down -v
docker-compose up -d

```

*The `-v` flag is critical to delete the old volume so the Init Container can reset permissions.*

**2. "Connection Refused" (Java Backend)**
If Java cannot connect to SFTP (`Connection refused`), ensure the config uses the **Docker Service Name** (`bank-sftp`), not `localhost`.

---

## 📜 License

This project is licensed under the MIT License - see the LICENSE file for details.