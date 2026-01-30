# 1. BASE STAGE (Runtime + LDAP Libs)
# This stage is used for the final image and for VS Fast Mode debugging.
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 8080
EXPOSE 8081

# CRITICAL ADDITION: Install LDAP libraries for Linux
# We switch to root to install, then switch back to the secure app user.
USER root
RUN apt-get update && apt-get install -y libldap-2.5-0 && rm -rf /var/lib/apt/lists/*
USER $APP_UID

# 2. BUILD STAGE
# This stage is used to build the service project
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src
COPY ["CSharpGateway.csproj", "."]
RUN dotnet restore "./CSharpGateway.csproj"
COPY . .
WORKDIR "/src/."
RUN dotnet build "./CSharpGateway.csproj" -c $BUILD_CONFIGURATION -o /app/build

# 3. PUBLISH STAGE
# This stage compiles the optimized DLLs
FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish "./CSharpGateway.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

# 4. FINAL STAGE
# Copies the compiled app into the Base image (which has the LDAP libs)
FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "CSharpGateway.dll"]