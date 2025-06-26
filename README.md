Echoit
======

A lightweight Spring Boot-based file sharing application that allows you to share files up to 10KB with anyone, anywhere. Perfect for quick peer-to-peer file transfers.

Features
--------

*   **Simple File Upload**: Drag & drop or click to upload files
    
*   **Instant Sharing**: Get a unique port-based invite code immediately
    
*   **Secure Transfer**: Direct peer-to-peer file sharing
    
*   **Modern UI**: Clean, responsive Next.js frontend
    
*   **Lightweight**: Minimal dependencies and resource usage
    
*   **Cross-Platform**: Works on any system with Java 17+
    

Tech Stack
----------

### Backend

*   Java 17
    
*   Built-in HTTP Server (no Spring Boot framework)
    
*   Maven for dependency management
    
*   Commons IO for file handling
    

### Frontend

*   Next.js 13+
    
*   React with TypeScript
    
*   Tailwind CSS for styling
    
*   Axios for HTTP requests
    

### Deployment

*   PM2 for process management
    
*   Nginx as reverse proxy
    
*   Docker support included
    

Quick Start
-----------

### Prerequisites

*   Java 17 or higher
    
*   Node.js 18 or higher
    
*   Maven 3.6+
    
*   PM2 (for production deployment)
    

### Local Development

1.  git clone https://github.com/manmohak07/echoit.git
    
2. cd echoit
    
3.  mvn clean package
    
4.  java -jar target/echoit-1.0-SNAPSHOT.jar
    
5.  cd ui

6. npm install

7. npm run dev
    
8.  Access the application
    
    *   Frontend: [http://localhost:3000](http://localhost:3000/)
        
    *   Backend API: [http://localhost:8080](http://localhost:8080/)
        

### Production Deployment (VPS)

For automated VPS deployment on Ubuntu/Debian:

Plain 
```
1. chmod +x vps-setup.sh  

2. ./vps-setup.sh   
```

This script will:

*   Install Java 17, Node.js, Maven, Nginx, and PM2
    
*   Build both frontend and backend
    
*   Configure Nginx reverse proxy
    
*   Start services with PM2
    
*   Set up auto-restart on boot
    

Usage
-----

### Sharing a File

1.  Go to the "Share a File" tab
    
2.  Drag & drop or click to select a file (max 10KB)
    
3.  Wait for upload to complete
    
4.  Share the generated invite code with the recipient
    

### Receiving a File

1.  Go to the "Receive a File" tab
    
2.  Enter the invite code you received
    
3.  Click download to get the file
    

API Endpoints
-------------

*   POST /upload - Upload a file and get a port number
    
*   GET /download/{port} - Download a file using the port number
    

Configuration
-------------

### File Size Limit

*   Currently set to 10KB. Files are temporarily stored in the system temp directory.
    

### Port Range

*   The application uses ports 49152-85535 for file sharing.
    

### Upload Directory

*   Files are stored in: {system-temp-dir}/echoit-uploads/
    

Docker Support
--------------

Build and run with Docker Compose

Plain 
```
docker-compose up --build
```

Project Structure
-----------------

Plain 
```
echoit/
├── src/main/java/echoit/          
│   ├── App.java                   
│   ├── controller/                
│   ├── service/                   
│   └── util/                      
├── ui/                            
│   ├── src/app/                   
│   └── src/components/            
├── vps-setup.sh                   
├── docker-compose.yml             
└── pom.xml      
```

Development Notes
-----------------

### Backend Architecture

*   Uses Java's built-in HttpServer for lightweight HTTP handling
    
*   Custom multipart form parser for file uploads
    
*   Thread-based file serving on dynamic ports
    
*   CORS enabled for frontend integration
    

### Frontend Features

*   Responsive design with Tailwind CSS
    
*   File drag & drop with visual feedback
    
*   Real-time upload/download progress
    
*   Error handling and user feedback
    

Known Limitations
-----------------

*   **File Size**: Limited to 10KB per file
    
*   **Persistence**: Files remain active until server restart
    
*   **Concurrent Users**: Designed for personal/small team use
    
*   **Security**: Basic implementation, suitable for trusted networks
    

Contributing
------------

This is a personal project, but feel free to fork and modify for your needs.
