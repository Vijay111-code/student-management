if (!(Test-Path out)) {
  javac -d out src\com\schoolapp\StudentManagementServer.java
}

java -cp out com.schoolapp.StudentManagementServer
