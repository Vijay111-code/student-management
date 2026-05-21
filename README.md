# Student Management Website

A ready-to-use Java student management website with:

- Student add, edit, delete, search, sort, and status updates
- Grade and status filters
- Dashboard statistics
- File-based persistence in `data/students.db`
- No external Java dependencies

## Run Locally

```powershell
javac -d out src\com\schoolapp\StudentManagementServer.java
java -cp out com.schoolapp.StudentManagementServer
```

Or use the included scripts:

```powershell
.\build.ps1
.\run.ps1
```

Open:

```text
http://localhost:8080
```

## Change Port

PowerShell:

```powershell
$env:PORT="9090"
java -cp out com.schoolapp.StudentManagementServer
```

## Deploy With Docker

```powershell
docker build -t student-management-java .
docker run -p 8080:8080 student-management-java
```

For persistent data in Docker:

```powershell
docker run -p 8080:8080 -v ${PWD}\data:/app/data student-management-java
```

## Deploy On Render

This project includes `render.yaml`, so Render can deploy it as a Docker web service.

1. Push this folder to a GitHub repository.
2. In Render, choose **New +** then **Blueprint**.
3. Connect the repository.
4. Render will read `render.yaml`, build the Dockerfile, and publish the site.

The app uses the `PORT` environment variable, which Render sets automatically for web services.

## Project Structure

```text
public/                         Frontend website files
src/com/schoolapp/              Java backend source
data/students.db                Created automatically on first run
Dockerfile                      Container deployment
render.yaml                     Render Blueprint deployment
```
