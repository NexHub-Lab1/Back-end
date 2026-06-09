#!/usr/bin/env python3
import urllib.request
import urllib.error
import json
import sys
import subprocess
from datetime import datetime, timedelta

BASE_URL = "http://localhost:8080"

def api_request(path, method="POST", data=None, token=None):
    url = f"{BASE_URL}{path}"
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    
    encoded_data = None
    if data is not None:
        encoded_data = json.dumps(data).encode("utf-8")
        
    try:
        with urllib.request.urlopen(req, data=encoded_data) as response:
            res_body = response.read().decode("utf-8")
            if res_body:
                return json.loads(res_body)
            return {"status": "success", "message": "No content"}
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        try:
            return json.loads(error_body)
        except json.JSONDecodeError:
            return {"status": "error", "message": f"HTTP Error {e.code}: {e.reason}", "raw": error_body}
    except Exception as e:
        return {"status": "error", "message": str(e)}

def signup_user(username, email, password):
    print(f"Registering user: {username} ({email})...")
    payload = {
        "username": username,
        "email": email,
        "password": password
    }
    res = api_request("/api/auth/signup", method="POST", data=payload)
    if res.get("status") == "success":
        print(f" -> User {username} registered successfully.")
        return res["data"]
    else:
        msg = res.get("message", "Unknown error")
        if "ya esta" in msg or "ya se encuentra" in msg or "Duplicate" in msg:
            print(f" -> User {username} already registered.")
            return None
        else:
            print(f" -> ERROR registering user {username}: {msg}")
            return None

def login_user(email, password):
    print(f"Logging in user: {email}...")
    payload = {
        "email": email,
        "password": password
    }
    res = api_request("/api/auth/login", method="POST", data=payload)
    if res.get("status") == "success":
        print(f" -> Login successful.")
        return res["data"]["token"], res["data"]["user"]["id"]
    else:
        print(f" -> ERROR logging in: {res.get('message')}")
        return None, None

def update_user_db_fields(username, streak_day, status):
    print(f"Updating user '{username}' in DB: streak_day={streak_day}, status='{status}'...")
    sql = f"UPDATE users SET streak_day = {streak_day}, status = '{status}' WHERE username = '{username}';"
    try:
        cmd = ["docker", "compose", "exec", "-T", "db", "psql", "-U", "postgres", "-d", "nexhub_db", "-c", sql]
        result = subprocess.run(cmd, capture_output=True, text=True, cwd=r"C:\Users\bauti\projects\NexHub")
        if result.returncode == 0:
            print(f" -> DB updated successfully for user {username}.")
            return True
        else:
            print(f" -> ERROR running SQL via Docker: {result.stderr.strip()}")
            return False
    except Exception as e:
        print(f" -> ERROR executing subprocess: {e}")
        return False

def create_project(token, owner_id, name, description, github_repo, status, tags):
    print(f"Creating project '{name}' ({status})...")
    payload = {
        "ownerId": owner_id,
        "name": name,
        "description": description,
        "githubRepo": github_repo,
        "status": status,
        "tags": tags
    }
    res = api_request("/api/projects", method="POST", data=payload, token=token)
    if res.get("status") == "success":
        project_id = res["data"]["id"]
        print(f" -> Project '{name}' created successfully with ID: {project_id}")
        return project_id
    else:
        print(f" -> ERROR creating project '{name}': {res.get('message')}")
        return None

def create_task(token, project_id, title, description, deliverables, reward_amount, reward_currency, deadline_days_ahead, max_attempts, skills):
    print(f"Creating task '{title}'...")
    deadline_date = (datetime.now() + timedelta(days=deadline_days_ahead)).strftime("%Y-%m-%d")
    payload = {
        "projectId": project_id,
        "title": title,
        "description": description,
        "deliverables": deliverables,
        "rewardAmount": reward_amount,
        "rewardCurrency": reward_currency,
        "deadline": deadline_date,
        "status": "OPEN",
        "maxAttempts": max_attempts,
        "recommendedSkills": skills
    }
    res = api_request("/api/tasks", method="POST", data=payload, token=token)
    if res.get("status") == "success":
        task_id = res["data"]["id"]
        print(f" -> Task '{title}' created successfully with ID: {task_id}")
        return task_id
    else:
        print(f" -> ERROR creating task '{title}': {res.get('message')}")
        return None

def assign_task(token, task_id, user_id, username):
    print(f"Assigning task ID {task_id} to {username}...")
    payload = {
        "taskId": task_id,
        "userId": user_id
    }
    res = api_request("/api/task-assignments", method="POST", data=payload, token=token)
    if res.get("status") == "success":
        assignment_id = res["data"]["id"]
        print(f" -> Assigned successfully with Assignment ID: {assignment_id}")
        return assignment_id
    else:
        print(f" -> ERROR assigning task: {res.get('message')}")
        return None

def update_assignment_status(token, assignment_id, status):
    print(f"Updating assignment ID {assignment_id} status to '{status}'...")
    payload = {
        "id": assignment_id,
        "status": status
    }
    res = api_request("/api/task-assignments/updateassignment", method="POST", data=payload, token=token)
    if res.get("status") == "success":
        print(f" -> Assignment updated successfully to '{status}'.")
        return True
    else:
        print(f" -> ERROR updating assignment: {res.get('message')}")
        return False

def submit_solution(token, assignment_id, pr_url):
    print(f"Submitting Pull Request for assignment ID {assignment_id}...")
    payload = {
        "assignmentId": assignment_id,
        "pullRequestUrl": pr_url
    }
    res = api_request("/api/task-submissions", method="POST", data=payload, token=token)
    if res.get("status") == "success":
        submission_id = res["data"]["id"]
        print(f" -> PR submitted successfully with Submission ID: {submission_id}")
        return submission_id
    else:
        print(f" -> ERROR submitting solution: {res.get('message')}")
        return None

def review_submission(token, submission_id, status, comments, reviewer_id):
    print(f"Reviewing submission ID {submission_id} ({status})...")
    payload = {
        "id": submission_id,
        "status": status,
        "reviewComments": comments,
        "reviewerId": reviewer_id
    }
    res = api_request("/api/task-submissions/updatesubmission", method="POST", data=payload, token=token)
    if res.get("status") == "success":
        print(f" -> Submission reviewed successfully.")
        return True
    else:
        print(f" -> ERROR reviewing submission: {res.get('message')}")
        return False

def main():
    print("====================================================")
    print("            NEXHUB DATABASE SEEDING SCRIPT          ")
    print("====================================================")

    # 1. Register Users
    users_info = [
        {"username": "alice", "email": "alice@example.com", "password": "Password123", "streak": 5, "status": "active"},
        {"username": "bob", "email": "bob@example.com", "password": "Password123", "streak": 0, "status": "active"},
        {"username": "charlie", "email": "charlie@example.com", "password": "Password123", "streak": 8, "status": "active"},
        {"username": "david", "email": "david@example.com", "password": "Password123", "streak": 2, "status": "active"},
        {"username": "elena", "email": "elena@example.com", "password": "Password123", "streak": 12, "status": "active"},
        {"username": "frank", "email": "frank@example.com", "password": "Password123", "streak": 0, "status": "deleted"},
        {"username": "grace", "email": "grace@example.com", "password": "Password123", "streak": 45, "status": "active"},
        {"username": "hector", "email": "hector@example.com", "password": "Password123", "streak": 3, "status": "deleted"},
        {"username": "ivan", "email": "ivan@example.com", "password": "Password123", "streak": 15, "status": "active"},
        {"username": "julia", "email": "julia@example.com", "password": "Password123", "streak": 22, "status": "active"},
        {"username": "kevin", "email": "kevin@example.com", "password": "Password123", "streak": 17, "status": "deleted"},
        {"username": "laura", "email": "laura@example.com", "password": "Password123", "streak": 84, "status": "deleted"},
        {"username": "mike", "email": "mike@example.com", "password": "Password123", "streak": 0, "status": "active"},
        {"username": "nancy", "email": "nancy@example.com", "password": "Password123", "streak": 1, "status": "active"},
        {"username": "oscar", "email": "oscar@example.com", "password": "Password123", "streak": 30, "status": "active"}
    ]
    
    for u in users_info:
        signup_user(u["username"], u["email"], u["password"])
        
    print("\n----------------------------------------------------")
    # 2. Login users and get tokens
    tokens = {}
    ids = {}
    for u in users_info:
        token, user_id = login_user(u["email"], u["password"])
        if token and user_id:
            tokens[u["username"]] = token
            ids[u["username"]] = user_id
            
    if "alice" not in tokens or "bob" not in tokens or "grace" not in tokens:
        print("ERROR: Could not fetch tokens for critical users. Seeding aborted.")
        sys.exit(1)

    print("\n----------------------------------------------------")
    # 3. Update User database-only fields (Streaks & Deleted Status)
    for u in users_info:
        update_user_db_fields(u["username"], u["streak"], u["status"])

    print("\n----------------------------------------------------")
    # 4. Create projects with different owner users, statuses, and descriptions
    # Project 1: Alice (OPEN)
    p1_id = create_project(
        token=tokens["alice"],
        owner_id=ids["alice"],
        name="NexHub Mobile App",
        description="A beautiful React Native client application for developers to browse projects, select reward tasks, and submit solution pull requests directly from their mobile devices.",
        github_repo="https://github.com/nexhub/nexhub-mobile",
        status="OPEN",
        tags=["react-native", "mobile", "typescript", "tailwind"]
    )
    
    # Project 2: Alice (OPEN)
    p2_id = create_project(
        token=tokens["alice"],
        owner_id=ids["alice"],
        name="AI Code Auditor Service",
        description="Spring Boot microservice integrated with LLM models to automatically audit code submissions, detect security issues, and check validations.",
        github_repo="https://github.com/nexhub/ai-auditor",
        status="OPEN",
        tags=["spring-boot", "java", "ai", "security"]
    )

    # Project 3: Grace (IN_PROGRESS)
    p3_id = create_project(
        token=tokens["grace"],
        owner_id=ids["grace"],
        name="Data Pipeline Engine",
        description="A high-performance pipeline written in Go. Designed to ingest, filter, and stream gigabytes of logs in real-time utilizing Apache Kafka.",
        github_repo="https://github.com/grace/pipeline",
        status="IN_PROGRESS",
        tags=["go", "kafka", "pipeline", "backend"]
    )

    # Project 4: Elena (COMPLETED)
    p4_id = create_project(
        token=tokens["elena"],
        owner_id=ids["elena"],
        name="Decentralized Escrow API",
        description="Web3 Solidity smart contract project designed to automatically lock, hold, and release task rewards without any intermediate broker.",
        github_repo="https://github.com/elena/smart-escrow",
        status="COMPLETED",
        tags=["solidity", "web3", "ethereum", "escrow"]
    )

    # Project 5: Alice (ARCHIVED)
    p5_id = create_project(
        token=tokens["alice"],
        owner_id=ids["alice"],
        name="Legacy XML Parser script",
        description="Deprecated script collection once used to parse XML payloads. Maintained purely for legacy audit requirements. Read-only and archived.",
        github_repo="https://github.com/nexhub/legacy-parser",
        status="ARCHIVED",
        tags=["python", "xml", "legacy"]
    )

    if not p1_id or not p2_id or not p3_id or not p4_id or not p5_id:
        print("ERROR: Could not create projects. Seeding aborted.")
        sys.exit(1)

    print("\n----------------------------------------------------")
    # 5. Create tasks under projects
    # Project 1 Tasks
    t1_id = create_task(
        token=tokens["alice"],
        project_id=p1_id,
        title="Implement Biometric Authentication",
        description="Add secure biometric authentication (FaceID/Fingerprint scan) using expo-local-authentication. The login screen should display prompts when users activate bio-auth.",
        deliverables="Updated Auth Screen, biometric utility helper functions, and app config permissions.",
        reward_amount=250.00,
        reward_currency="USD",
        deadline_days_ahead=30,
        max_attempts=3,
        skills=["react-native", "security", "typescript"]
    )
    
    t2_id = create_task(
        token=tokens["alice"],
        project_id=p1_id,
        title="Optimize Tasks Scroll Performance",
        description="Improve flatlist scroll performance in the dashboard. Avoid component re-renders, implement virtual scrolling pagination, and optimize image asset caching.",
        deliverables="Flatlist component refactor and memory usage logs indicating rendering improvements.",
        reward_amount=150.00,
        reward_currency="USD",
        deadline_days_ahead=14,
        max_attempts=2,
        skills=["react-native", "performance"]
    )

    # Project 2 Tasks
    t3_id = create_task(
        token=tokens["alice"],
        project_id=p2_id,
        title="Enforce JSR-380 input validations",
        description="Implement validation on DTO records (Login, Signup, Project, and Tasks) using spring-boot-starter-validation annotations like @Size, @NotBlank, and @Email. Write a global handler for method argument validations.",
        deliverables="DTO validation additions, @RestControllerAdvice exception handler updates, and clean test suite coverage.",
        reward_amount=180.00,
        reward_currency="USD",
        deadline_days_ahead=15,
        max_attempts=3,
        skills=["spring-boot", "java", "validation"]
    )

    # Project 3 Tasks (Grace's Project) - Task with many proposals
    t5_id = create_task(
        token=tokens["grace"],
        project_id=p3_id,
        title="Implement Kafka Consumer retry logic",
        description="Build a resilient retry mechanism for parsing log streams when external services are temporarily unavailable. Config exponential backoff retries with dead-letter queue (DLQ) support.",
        deliverables="Kafka consumer retry queue configurations, backoff handler classes, and integration tests.",
        reward_amount=300.00,
        reward_currency="USD",
        deadline_days_ahead=20,
        max_attempts=5,
        skills=["go", "kafka", "pipeline"]
    )

    t6_id = create_task(
        token=tokens["grace"],
        project_id=p3_id,
        title="Add unit tests for Log Parser",
        description="Write unit tests for the regex-based log parsing engine to bring code coverage up to 90%.",
        deliverables="Unit tests covering logs parser engine edge cases.",
        reward_amount=100.00,
        reward_currency="USD",
        deadline_days_ahead=10,
        max_attempts=3,
        skills=["go", "testing"]
    )

    print("\n----------------------------------------------------")
    # 6. Simulate Assignments & Submissions
    
    # Task 1: Bob
    a1_id = assign_task(tokens["bob"], t1_id, ids["bob"], "bob")
    if a1_id:
        sub1_id = submit_solution(tokens["bob"], a1_id, "https://github.com/nexhub/nexhub-mobile/pull/124")
        if sub1_id:
            review_submission(tokens["alice"], sub1_id, "APPROVED", "Fantastic work. Clean architecture, complete test coverage and proper permissions configuration.", ids["alice"])

    # Task 3: Charlie
    a2_id = assign_task(tokens["charlie"], t3_id, ids["charlie"], "charlie")
    if a2_id:
        sub2_id = submit_solution(tokens["charlie"], a2_id, "https://github.com/nexhub/ai-auditor/pull/8")
        if sub2_id:
            review_submission(tokens["alice"], sub2_id, "REJECTED", "Input boundaries are validated properly, but there are no unit tests verifying the exception advice translates validation messages correctly. Please add controller tests and resubmit.", ids["alice"])

    # Task 5: MANY SUBMITTED PROPOSALS (Bob, Charlie, David, Ivan)
    print("\nSimulating many submitted proposals for Task 5 ('Implement Kafka Consumer retry logic')...")
    
    # 1. Bob's Submission (Pending)
    abob_id = assign_task(tokens["bob"], t5_id, ids["bob"], "bob")
    if abob_id:
        submit_solution(tokens["bob"], abob_id, "https://github.com/grace/pipeline/pull/101")
        update_assignment_status(tokens["bob"], abob_id, "cancelled")
        
    # 2. Charlie's Submissions (1st Rejected, 2nd Pending)
    acharlie_id = assign_task(tokens["charlie"], t5_id, ids["charlie"], "charlie")
    if acharlie_id:
        sub_c1 = submit_solution(tokens["charlie"], acharlie_id, "https://github.com/grace/pipeline/pull/105")
        if sub_c1:
            review_submission(tokens["grace"], sub_c1, "REJECTED", "No hay manejo de backoff exponencial en los reintentos. Favor corregir e intentar de nuevo.", ids["grace"])
        # Charlie resubmits (Attempt 2)
        submit_solution(tokens["charlie"], acharlie_id, "https://github.com/grace/pipeline/pull/110")
        update_assignment_status(tokens["charlie"], acharlie_id, "cancelled")

    # 3. David's Submission (Approved)
    adavid_id = assign_task(tokens["david"], t5_id, ids["david"], "david")
    if adavid_id:
        sub_d = submit_solution(tokens["david"], adavid_id, "https://github.com/grace/pipeline/pull/115")
        if sub_d:
            review_submission(tokens["grace"], sub_d, "APPROVED", "Excelente implementación. La cola de reintentos y el DLQ funcionan perfectamente bajo estrés.", ids["grace"])

    # 4. Ivan's Submission (Pending)
    aivan_id = assign_task(tokens["ivan"], t5_id, ids["ivan"], "ivan")
    if aivan_id:
        submit_solution(tokens["ivan"], aivan_id, "https://github.com/grace/pipeline/pull/120")

    print("\n====================================================")
    print("          SEEDING COMPLETED SUCCESSFULLY!           ")
    print("====================================================")

if __name__ == "__main__":
    main()
