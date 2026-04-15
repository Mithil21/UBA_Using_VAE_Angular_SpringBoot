@echo off
echo Starting UBA Research System...
echo.

echo Installing Python dependencies...
cd "uba-research-pythonAI"
pip install -r requirements.txt
echo.

echo Starting Python AI Flask Server...
start "Python AI Server" cmd /k "python flask_server.py"
timeout /t 3

echo Starting Spring Boot Backend...
cd "..\uba-research-backend"
start "Spring Boot Backend" cmd /k "mvn spring-boot:run"
timeout /t 10

echo Starting Angular Frontend...
cd "..\uba-research"
start "Angular Frontend" cmd /k "npm install && ng serve"
timeout /t 5

echo.
echo ========================================
echo UBA Research System Starting...
echo ========================================
echo Python AI Server: http://localhost:5000
echo Spring Boot Backend: http://localhost:8080
echo Angular Frontend: http://localhost:4200
echo ========================================
echo.
echo Press any key to run system tests...
pause

echo Running system tests...
cd ..
python test_uba_system.py

pause