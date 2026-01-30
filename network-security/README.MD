This is an excellent way to learn. By "containerizing" the entire architecture, you create a portable lab that mimics a complex enterprise environment (DMZ, Internal Network, External Internet) right on your laptop.

We will use **Docker Compose** to orchestrate five containers.

### **The Local Lab Architecture**

1. **`health-net` (172.20.0.0/16):** A custom bridge network that simulates our "Intranet".
2. **`mock-dns` (172.20.0.2):** A **CoreDNS** server that resolves `secure.bank-partner.com` to our local SFTP container.
3. **`partner-sftp` (172.20.0.3):** A Linux server running SSH/SFTP.
4. **`active-directory`:** An **OpenLDAP** server (simulating AD) with pre-loaded users.
5. **`gateway-csharp`:** Your .NET Web API.
6. **`backend-java`:** Your Java TCP Processor.

---

### **Step 1: The Folder Structure**

Create a folder named `HealthProviderLab` and set it up like this:

```text
HealthProviderLab/
├── docker-compose.yml       # The Master Plan
├── config/
│   ├── dns/
│   │   └── Corefile         # DNS Configuration
│   └── ldap/
│       └── users.ldif       # Active Directory Users
├── src/
│   ├── CSharpGateway/       # Your C# Project
│   │   └── Dockerfile
│   └── JavaBackend/         # Your Java Project
│       └── Dockerfile

```

---

### **Step 2: The Master Blueprint (`docker-compose.yml`)**

This file defines the entire world. Copy this into `docker-compose.yml`.

```yaml
version: '3.8'

services:
  # 1. THE DNS SERVER (Simulates Corporate DNS)
  mock-dns:
    image: coredns/coredns
    container_name: corporate-dns
    volumes:
      - ./config/dns:/root/config
    command: -conf /root/config/Corefile
    networks:
      health-net:
        ipv4_address: 172.20.0.2

  # 2. THE PARTNER BANK (Secure SFTP)
  partner-sftp:
    image: atmoz/sftp
    container_name: bank-sftp
    ports:
      - "2222:22" # Map local port 2222 to container 22
    # Create user: pass:UID
    command: bank_user:SecretPass123!:1001
    networks:
      health-net:
        ipv4_address: 172.20.0.3

  # 3. ACTIVE DIRECTORY (Simulated via OpenLDAP)
  active-directory:
    image: bitnami/openldap:latest
    container_name: corp-ad
    ports:
      - "1389:1389"
    environment:
      - LDAP_ADMIN_USERNAME=admin
      - LDAP_ADMIN_PASSWORD=adminpassword
      - LDAP_ROOT=dc=healthprovider,dc=com
    volumes:
      - ./config/ldap:/docker-entrypoint-initdb.d # Auto-load users
    networks:
      - health-net

  # 4. C# GATEWAY (The Web API)
  gateway-csharp:
    build: ./src/CSharpGateway
    container_name: gateway-web
    ports:
      - "5000:8080" # Access via http://localhost:5000
    environment:
      - ActiveDirectory__Server=active-directory
      - ActiveDirectory__Port=1389
    depends_on:
      - active-directory
      - backend-java
    networks:
      - health-net

  # 5. JAVA BACKEND (The Processor)
  backend-java:
    build: ./src/JavaBackend
    container_name: backend-proc
    dns:
      - 172.20.0.2 # <--- CRITICAL: Forces Java to use our Mock DNS
    networks:
      - health-net

networks:
  health-net:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16

```

---

### **Step 3: Setup the Infrastructure Services**

#### **A. DNS Configuration (CoreDNS)**

Create `config/dns/Corefile`. This tells the DNS server: *"If anyone asks for `secure.bank-partner.com`, send them to `172.20.0.3` (the SFTP container)."*

```text
.:53 {
    forward . 8.8.8.8
    log
    errors
    hosts {
        172.20.0.3 secure.bank-partner.com
        fallthrough
    }
}

```

#### **B. Active Directory Users (LDIF)**

Create `config/ldap/users.ldif`. This creates a user "dr_smith" so you can log in.

```ldif
dn: ou=users,dc=healthprovider,dc=com
objectClass: organizationalUnit
ou: users

dn: cn=dr_smith,ou=users,dc=healthprovider,dc=com
objectClass: inetOrgPerson
cn: dr_smith
sn: Smith
uid: dr_smith
userPassword: DoctorPassword!
mail: smith@healthprovider.com

```

---

### **Step 4: The Application Dockerfiles**

You need to "Dockerize" your code so it can run in this network.

#### **C# Gateway (`src/CSharpGateway/Dockerfile`)**

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /app
COPY . .
RUN dotnet publish -c Release -o out

FROM mcr.microsoft.com/dotnet/aspnet:8.0
WORKDIR /app
COPY --from=build /app/out .
# Install LDAP libs for Linux
RUN apt-get update && apt-get install -y libldap-2.5-0 
ENTRYPOINT ["dotnet", "CSharpGateway.dll"]

```

#### **Java Backend (`src/JavaBackend/Dockerfile`)**

```dockerfile
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]

```

---

### **Step 5: Code Adjustments for Docker**

You need to slightly update your code to look for these environment variables and container names.

**1. C# Update (`ClaimsController.cs`):**
Change the hardcoded "localhost" to use the container name `backend-proc` (which Docker DNS resolves automatically) and the config for AD.

```csharp
// Connect to Java Backend
await client.ConnectAsync("backend-java", 9999); 

// Connect to LDAP (Active Directory)
string ldapServer = Environment.GetEnvironmentVariable("ActiveDirectory__Server"); // "active-directory"
int ldapPort = int.Parse(Environment.GetEnvironmentVariable("ActiveDirectory__Port")); // 1389

```

**2. Java Update (`BackendService.java`):**
Ensure your Java code prints the DNS resolution. Because we forced the DNS server in `docker-compose.yml`, this line:

```java
InetAddress address = InetAddress.getByName("secure.bank-partner.com");

```

...will now magically resolve to `172.20.0.3` (your local SFTP container), **proving that your custom DNS server is working.**

---

### **How to Run the Project**

1. **Build and Start:**
```bash
docker-compose up --build

```


2. **Test the SFTP (Manual Check):**
   Open a terminal and try to connect to your "Bank" manually:
```bash
sftp -P 2222 bank_user@localhost
# Password: SecretPass123!

```


3. **Run the Scenario:**
   Use Postman or Curl to hit your C# API:
```bash
curl -X POST https://localhost:5000/api/claims/upload \
     -H "username: dr_smith" \
     -H "password: DoctorPassword!" \
     -F "file=@patient_claim.xml"

```



### **What happens behind the scenes?**

1. **Request:** Hits port 5000 on your laptop -> forwards to **C# Container**.
2. **Auth:** C# talks to **OpenLDAP Container** on port 1389. Verifies `dr_smith`.
3. **Internal Handoff:** C# opens TCP socket to **Java Container** (hostname `backend-java`).
4. **Processing:** Java receives command. It asks **CoreDNS Container** (172.20.0.2): *"Who is https://www.google.com/url?sa=E&source=gmail&q=secure.bank-partner.com?"*
5. **DNS Reply:** CoreDNS says: *"That is 172.20.0.3"*.
6. **Transfer:** Java opens SSH tunnel to **SFTP Container** (172.20.0.3) and uploads the file.

You have now simulated a full enterprise infrastructure stack on your local machine!