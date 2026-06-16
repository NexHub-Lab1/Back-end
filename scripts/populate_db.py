#!/usr/bin/env python3
import urllib.request
import urllib.error
import json
import sys
import subprocess
import os
import ssl
from datetime import datetime, timedelta

# Dynamically resolve project root path
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))

def load_env():
    """Parses .env file from project root at runtime to load database credentials."""
    env = {}
    env_path = os.path.join(PROJECT_ROOT, ".env")
    if os.path.exists(env_path):
        try:
            with open(env_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#"):
                        parts = line.split("=", 1)
                        if len(parts) == 2:
                            key = parts[0].strip()
                            val = parts[1].strip().strip("'\"")
                            env[key] = val
        except Exception as e:
            print(f"Warning: Could not parse .env: {e}")
    return env

# Load environment variables
ENV = load_env()
BASE_URL = os.environ.get("NEXHUB_BASE_URL", ENV.get("NEXHUB_BASE_URL", "http://localhost:8080")).rstrip("/")
DB_USER = os.environ.get("DB_USER", ENV.get("DB_USER", "postgres"))
DB_NAME = os.environ.get("DB_NAME", ENV.get("DB_NAME", "nexhub_db"))
DIRECT_DB_UPDATES = os.environ.get(
    "NEXHUB_DIRECT_DB_UPDATES",
    ENV.get("NEXHUB_DIRECT_DB_UPDATES", "true")
).lower() in ("1", "true", "yes", "y")
INSECURE_SSL = os.environ.get(
    "NEXHUB_INSECURE_SSL",
    ENV.get("NEXHUB_INSECURE_SSL", "false")
).lower() in ("1", "true", "yes", "y")
SSL_CONTEXT = ssl._create_unverified_context() if INSECURE_SSL else None
TASK_CURRENCY = "ARS"
NORMALIZE_EXISTING_TASK_CURRENCIES = os.environ.get(
    "NEXHUB_NORMALIZE_TASK_CURRENCIES",
    ENV.get("NEXHUB_NORMALIZE_TASK_CURRENCIES", "true")
).lower() in ("1", "true", "yes", "y")

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
        with urllib.request.urlopen(req, data=encoded_data, context=SSL_CONTEXT) as response:
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

def activate_all_users_in_db():
    if not DIRECT_DB_UPDATES:
        print("Skipping direct DB user activation; NEXHUB_DIRECT_DB_UPDATES=false.")
        return True

    print("Temporarily activating all users in DB to allow login...")
    sql = "UPDATE users SET status = 'active';"
    try:
        cmd = ["docker", "compose", "exec", "-T", "db", "psql", "-U", DB_USER, "-d", DB_NAME, "-c", sql]
        result = subprocess.run(cmd, capture_output=True, text=True, cwd=PROJECT_ROOT)
        if result.returncode == 0:
            print(" -> All users activated temporarily in DB.")
            return True
        else:
            print(f" -> WARNING: could not activate users via SQL: {result.stderr.strip()}")
            return False
    except Exception as e:
        print(f" -> ERROR activating users: {e}")
        return False

def update_user_db_fields(username, streak_day, status, reputation_score=0):
    if not DIRECT_DB_UPDATES:
        print(f"Skipping direct DB update for user '{username}'; NEXHUB_DIRECT_DB_UPDATES=false.")
        return True

    print(f"Updating user '{username}' in DB: streak_day={streak_day}, status='{status}', reputation_score={reputation_score}...")
    sql = f"UPDATE users SET streak_day = {streak_day}, status = '{status}', reputation_score = {reputation_score} WHERE username = '{username}';"
    try:
        cmd = ["docker", "compose", "exec", "-T", "db", "psql", "-U", DB_USER, "-d", DB_NAME, "-c", sql]
        result = subprocess.run(cmd, capture_output=True, text=True, cwd=PROJECT_ROOT)
        if result.returncode == 0:
            print(f" -> DB updated successfully for user {username}.")
            return True
        else:
            print(f" -> ERROR running SQL via Docker: {result.stderr.strip()}")
            return False
    except Exception as e:
        print(f" -> ERROR executing subprocess: {e}")
        return False

def paginated_content(res):
    data = res.get("data") if isinstance(res, dict) else None
    if isinstance(data, dict) and isinstance(data.get("content"), list):
        return data["content"]
    return []

def find_project_by_name(name):
    res = api_request("/api/projects?size=200", method="GET")
    for project in paginated_content(res):
        if str(project.get("name", "")).lower() == name.lower():
            return project.get("id")
    return None

def find_task_by_project_and_title(project_id, title):
    if not project_id:
        return None

    res = api_request(f"/api/tasks/project/{project_id}?size=200", method="GET")
    for task in paginated_content(res):
        if str(task.get("title", "")).lower() == title.lower():
            return task.get("id")
    return None

def list_all_tasks():
    tasks = []
    page = 0
    while True:
        res = api_request(f"/api/tasks?page={page}&size=100", method="GET")
        data = res.get("data") if isinstance(res, dict) else None
        content = paginated_content(res)
        tasks.extend(content)
        if not isinstance(data, dict) or data.get("last", True):
            break
        page += 1
    return tasks

def normalize_existing_task_currencies(token):
    if not NORMALIZE_EXISTING_TASK_CURRENCIES:
        print("Skipping task currency normalization; NEXHUB_NORMALIZE_TASK_CURRENCIES=false.")
        return

    print(f"Normalizing existing non-{TASK_CURRENCY} task currencies to {TASK_CURRENCY} where allowed...")
    changed = 0
    skipped = 0
    for task in list_all_tasks():
        task_id = task.get("id")
        current_currency = str(task.get("rewardCurrency") or "").upper()
        if not task_id or current_currency == TASK_CURRENCY:
            continue

        payload = {
            "id": task_id,
            "rewardCurrency": TASK_CURRENCY
        }
        res = api_request("/api/tasks/updatetask", method="POST", data=payload, token=token)
        if res.get("status") == "success":
            changed += 1
            print(f" -> Task ID {task_id} currency updated from {current_currency} to {TASK_CURRENCY}.")
        else:
            skipped += 1
            print(f" -> Skipped task ID {task_id}: {res.get('message')}")

    print(f" -> Currency normalization finished. Updated: {changed}. Skipped: {skipped}.")

def create_project(token, owner_id, name, description, github_repo, status, tags):
    existing_project_id = find_project_by_name(name)
    if existing_project_id:
        print(f"Project '{name}' already exists with ID: {existing_project_id}. Reusing it.")
        return existing_project_id

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

def create_task(token, project_id, title, description, deliverables, reward_amount, reward_currency, deadline_days_ahead, max_attempts, skills, min_reputation=0, collaborative=False):
    existing_task_id = find_task_by_project_and_title(project_id, title)
    if existing_task_id:
        print(f"Task '{title}' already exists with ID: {existing_task_id}. Reusing it.")
        return existing_task_id

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
        "minReputation": min_reputation,
        "collaborative": collaborative,
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

def review_submission(token, submission_id, status, comments, reviewer_id, rejection_reason=None):
    print(f"Reviewing submission ID {submission_id} ({status})...")
    payload = {
        "id": submission_id,
        "status": status,
        "reviewComments": comments,
        "reviewerId": reviewer_id
    }
    if rejection_reason:
        payload["rejectionReason"] = rejection_reason
    res = api_request("/api/task-submissions/updatesubmission", method="POST", data=payload, token=token)
    if res.get("status") == "success":
        print(f" -> Submission reviewed successfully.")
        return True
    else:
        print(f" -> ERROR reviewing submission: {res.get('message')}")
        return False

def seed_marketplace_catalog(tokens, ids):
    print("\nSeeding broader marketplace catalog...")
    project_specs = [
        {
            "owner": "bob",
            "name": "Realtime Collaboration Board",
            "description": "A collaborative whiteboard for distributed product teams with live cursors, sticky notes, comments, and role-based sharing.",
            "github_repo": "https://github.com/nexhub/realtime-board",
            "status": "OPEN",
            "tags": ["react", "websocket", "collaboration", "typescript"],
            "tasks": [
                {
                    "title": "Add optimistic updates for sticky notes",
                    "description": "Implement optimistic UI updates for creating, editing, and deleting sticky notes while the websocket acknowledgement is pending.",
                    "deliverables": "Optimistic state reducer, rollback handling for failed events, and regression tests for note mutations.",
                    "reward_amount": 85000,
                    "deadline_days_ahead": 16,
                    "max_attempts": 3,
                    "skills": ["react", "typescript", "state-management"],
                },
                {
                    "title": "Build live cursor presence indicators",
                    "description": "Show active collaborators on the board with colored cursors, user initials, and idle state transitions after inactivity.",
                    "deliverables": "Presence component, websocket event handling, and responsive cursor labels.",
                    "reward_amount": 115000,
                    "deadline_days_ahead": 20,
                    "max_attempts": 3,
                    "skills": ["react", "websocket", "ui"],
                },
                {
                    "title": "Create board export to PDF",
                    "description": "Add a server-assisted export flow that captures board content and downloads a readable PDF snapshot.",
                    "deliverables": "Export button, backend export endpoint integration, loading/error states, and sample PDF output.",
                    "reward_amount": 140000,
                    "deadline_days_ahead": 24,
                    "max_attempts": 2,
                    "skills": ["pdf", "react", "backend"],
                    "min_reputation": 20,
                },
            ],
        },
        {
            "owner": "charlie",
            "name": "GreenCommerce API",
            "description": "A Spring Boot commerce backend for sustainable products, with catalog management, carts, coupons, and payment-ready order flows.",
            "github_repo": "https://github.com/nexhub/greencommerce-api",
            "status": "OPEN",
            "tags": ["spring-boot", "java", "postgresql", "payments"],
            "tasks": [
                {
                    "title": "Implement coupon validation engine",
                    "description": "Create coupon validation rules for expiration, usage limits, minimum cart amount, and product category restrictions.",
                    "deliverables": "Coupon service, repository queries, DTO validations, and unit tests for rule combinations.",
                    "reward_amount": 125000,
                    "deadline_days_ahead": 18,
                    "max_attempts": 3,
                    "skills": ["java", "spring-boot", "testing"],
                },
                {
                    "title": "Add inventory reservation on checkout",
                    "description": "Reserve stock while an order payment is pending and release it automatically if checkout expires.",
                    "deliverables": "Inventory reservation model, scheduled cleanup, and integration tests for race conditions.",
                    "reward_amount": 210000,
                    "deadline_days_ahead": 28,
                    "max_attempts": 3,
                    "skills": ["java", "postgresql", "concurrency"],
                    "min_reputation": 50,
                },
                {
                    "title": "Document REST API examples",
                    "description": "Write practical API examples for catalog, cart, coupon, and order endpoints using request and response payloads.",
                    "deliverables": "Markdown documentation, curl examples, and one Postman collection export.",
                    "reward_amount": 55000,
                    "deadline_days_ahead": 9,
                    "max_attempts": 2,
                    "skills": ["documentation", "api", "markdown"],
                },
            ],
        },
        {
            "owner": "julia",
            "name": "Campus Events Platform",
            "description": "A web platform for university clubs to publish events, manage registrations, and track attendance with QR codes.",
            "github_repo": "https://github.com/nexhub/campus-events",
            "status": "OPEN",
            "tags": ["react", "node", "qr", "events"],
            "tasks": [
                {
                    "title": "Build QR check-in scanner view",
                    "description": "Create a mobile-friendly scanner screen for event organizers to validate attendee QR codes at the door.",
                    "deliverables": "Scanner UI, permission handling, check-in API integration, and empty/error states.",
                    "reward_amount": 95000,
                    "deadline_days_ahead": 13,
                    "max_attempts": 3,
                    "skills": ["react", "qr", "mobile-ui"],
                },
                {
                    "title": "Add attendee CSV export",
                    "description": "Allow event owners to export registrations with attendance status and ticket metadata as CSV.",
                    "deliverables": "Export endpoint integration, CSV formatting, and access-control checks.",
                    "reward_amount": 70000,
                    "deadline_days_ahead": 12,
                    "max_attempts": 2,
                    "skills": ["csv", "react", "backend"],
                },
                {
                    "title": "Create event reminder notifications",
                    "description": "Send reminders to registered attendees 24 hours before an event and when an event time changes.",
                    "deliverables": "Notification trigger logic, message templates, and tests for schedule changes.",
                    "reward_amount": 135000,
                    "deadline_days_ahead": 22,
                    "max_attempts": 3,
                    "skills": ["notifications", "scheduler", "typescript"],
                    "collaborative": True,
                },
            ],
        },
        {
            "owner": "david",
            "name": "Observability Dashboard",
            "description": "A dashboard that visualizes API latency, error rate, and deployment health from service metrics.",
            "github_repo": "https://github.com/nexhub/observability-dashboard",
            "status": "OPEN",
            "tags": ["react", "metrics", "charts", "observability"],
            "tasks": [
                {
                    "title": "Create latency percentile chart",
                    "description": "Visualize p50, p95, and p99 latency over time with filters for service and environment.",
                    "deliverables": "Chart component, filter controls, mock data states, and responsive layout.",
                    "reward_amount": 100000,
                    "deadline_days_ahead": 15,
                    "max_attempts": 3,
                    "skills": ["react", "charts", "metrics"],
                },
                {
                    "title": "Add incident annotation timeline",
                    "description": "Display deploys and incidents as annotations over metric charts so users can correlate spikes with events.",
                    "deliverables": "Timeline annotations, tooltip copy, and integration with incident payloads.",
                    "reward_amount": 145000,
                    "deadline_days_ahead": 21,
                    "max_attempts": 3,
                    "skills": ["react", "visualization", "ux"],
                    "min_reputation": 30,
                },
                {
                    "title": "Implement health summary cards",
                    "description": "Build summary cards for uptime, error budget, open incidents, and slowest endpoints.",
                    "deliverables": "Summary card components, API mappers, loading states, and unit tests.",
                    "reward_amount": 90000,
                    "deadline_days_ahead": 11,
                    "max_attempts": 2,
                    "skills": ["react", "testing", "api"],
                },
            ],
        },
        {
            "owner": "nancy",
            "name": "Health Appointment Scheduler",
            "description": "A scheduling app for clinics to coordinate appointment slots, reminders, cancellations, and doctor availability.",
            "github_repo": "https://github.com/nexhub/clinic-scheduler",
            "status": "OPEN",
            "tags": ["java", "react", "calendar", "healthcare"],
            "tasks": [
                {
                    "title": "Prevent overlapping doctor appointments",
                    "description": "Add backend validation to reject conflicting appointments for the same doctor and time window.",
                    "deliverables": "Validation service, database query, and tests covering overlapping and adjacent slots.",
                    "reward_amount": 130000,
                    "deadline_days_ahead": 19,
                    "max_attempts": 3,
                    "skills": ["java", "spring-boot", "postgresql"],
                },
                {
                    "title": "Build calendar week navigation",
                    "description": "Implement a weekly calendar view with previous/next controls, today shortcut, and appointment density indicators.",
                    "deliverables": "Calendar UI, date utilities, and responsive behavior for mobile.",
                    "reward_amount": 110000,
                    "deadline_days_ahead": 17,
                    "max_attempts": 3,
                    "skills": ["react", "calendar", "typescript"],
                },
                {
                    "title": "Add cancellation reason analytics",
                    "description": "Capture cancellation reasons and show a small analytics summary for clinic admins.",
                    "deliverables": "Cancellation form field, reason aggregation endpoint, and admin summary UI.",
                    "reward_amount": 155000,
                    "deadline_days_ahead": 26,
                    "max_attempts": 3,
                    "skills": ["analytics", "java", "react"],
                },
            ],
        },
        {
            "owner": "oscar",
            "name": "Open Source Docs Portal",
            "description": "A documentation portal with markdown import, full-text search, versioned pages, and contributor-friendly editing.",
            "github_repo": "https://github.com/nexhub/docs-portal",
            "status": "OPEN",
            "tags": ["markdown", "search", "documentation", "nextjs"],
            "tasks": [
                {
                    "title": "Implement markdown heading anchors",
                    "description": "Generate stable heading anchors and a table of contents from markdown content.",
                    "deliverables": "Markdown parser integration, anchor links, and tests for duplicate headings.",
                    "reward_amount": 65000,
                    "deadline_days_ahead": 8,
                    "max_attempts": 2,
                    "skills": ["markdown", "typescript", "frontend"],
                },
                {
                    "title": "Add Algolia search adapter",
                    "description": "Create a search adapter abstraction and implement an Algolia-backed provider for docs pages.",
                    "deliverables": "Search adapter interface, Algolia provider, and mocked tests.",
                    "reward_amount": 160000,
                    "deadline_days_ahead": 23,
                    "max_attempts": 3,
                    "skills": ["search", "typescript", "api"],
                    "min_reputation": 40,
                },
                {
                    "title": "Improve empty states for missing docs",
                    "description": "Design and implement clear empty states for missing pages, no search results, and unpublished versions.",
                    "deliverables": "Empty state components, copy updates, and visual QA across desktop and mobile.",
                    "reward_amount": 48000,
                    "deadline_days_ahead": 7,
                    "max_attempts": 2,
                    "skills": ["ui", "ux", "react"],
                },
            ],
        },
        {
            "owner": "elena",
            "name": "FinOps Invoice Processor",
            "description": "A backend workflow that extracts invoice metadata, validates totals, and prepares finance review queues.",
            "github_repo": "https://github.com/nexhub/invoice-processor",
            "status": "OPEN",
            "tags": ["java", "ocr", "postgresql", "finance"],
            "tasks": [
                {
                    "title": "Add invoice duplicate detection",
                    "description": "Detect duplicate invoices using vendor tax ID, invoice number, currency, and total amount.",
                    "deliverables": "Duplicate detection service, repository queries, and validation tests.",
                    "reward_amount": 150000,
                    "deadline_days_ahead": 18,
                    "max_attempts": 3,
                    "skills": ["java", "postgresql", "testing"],
                },
                {
                    "title": "Create OCR confidence review queue",
                    "description": "Route low-confidence OCR extractions to a manual review queue with fields highlighted for correction.",
                    "deliverables": "Review queue API, DTOs, and frontend state contract documentation.",
                    "reward_amount": 190000,
                    "deadline_days_ahead": 27,
                    "max_attempts": 3,
                    "skills": ["java", "ocr", "workflow"],
                    "min_reputation": 60,
                },
                {
                    "title": "Export reviewed invoices as JSON",
                    "description": "Add a download endpoint for reviewed invoices in a clean JSON structure ready for finance import.",
                    "deliverables": "JSON export endpoint, schema documentation, and sample payloads.",
                    "reward_amount": 80000,
                    "deadline_days_ahead": 14,
                    "max_attempts": 2,
                    "skills": ["java", "json", "api"],
                },
            ],
        },
        {
            "owner": "mike",
            "name": "Learning Analytics Toolkit",
            "description": "A toolkit for educators to track course engagement, assignment completion, and student progress trends.",
            "github_repo": "https://github.com/nexhub/learning-analytics",
            "status": "OPEN",
            "tags": ["python", "react", "analytics", "education"],
            "tasks": [
                {
                    "title": "Build assignment completion cohort table",
                    "description": "Show completion rates by course cohort, week, and assignment type.",
                    "deliverables": "Cohort table UI, API mappers, and deterministic test data fixtures.",
                    "reward_amount": 105000,
                    "deadline_days_ahead": 15,
                    "max_attempts": 3,
                    "skills": ["react", "analytics", "tables"],
                },
                {
                    "title": "Add Python engagement scoring function",
                    "description": "Implement a scoring function that combines logins, submissions, forum activity, and lecture progress.",
                    "deliverables": "Python scoring module, docstring examples, and tests for edge cases.",
                    "reward_amount": 125000,
                    "deadline_days_ahead": 20,
                    "max_attempts": 3,
                    "skills": ["python", "analytics", "testing"],
                },
                {
                    "title": "Create risk alert notification template",
                    "description": "Create notification copy and trigger conditions for students at risk of falling behind.",
                    "deliverables": "Notification template, trigger logic documentation, and UI preview state.",
                    "reward_amount": 75000,
                    "deadline_days_ahead": 10,
                    "max_attempts": 2,
                    "skills": ["notifications", "product", "ux"],
                    "collaborative": True,
                },
            ],
        },
    ]

    created_project_ids = {}
    for spec in project_specs:
        owner = spec["owner"]
        if owner not in tokens or owner not in ids:
            print(f"Skipping project '{spec['name']}' because owner '{owner}' is unavailable.")
            continue

        project_id = create_project(
            token=tokens[owner],
            owner_id=ids[owner],
            name=spec["name"],
            description=spec["description"],
            github_repo=spec["github_repo"],
            status=spec["status"],
            tags=spec["tags"],
        )
        if not project_id:
            continue

        created_project_ids[spec["name"]] = project_id
        for task in spec["tasks"]:
            create_task(
                token=tokens[owner],
                project_id=project_id,
                title=task["title"],
                description=task["description"],
                deliverables=task["deliverables"],
                reward_amount=task["reward_amount"],
                reward_currency=TASK_CURRENCY,
                deadline_days_ahead=task["deadline_days_ahead"],
                max_attempts=task["max_attempts"],
                skills=task["skills"],
                min_reputation=task.get("min_reputation", 0),
                collaborative=task.get("collaborative", False),
            )

    print(f" -> Marketplace catalog ready: {len(created_project_ids)} projects processed.")

def main():
    print("====================================================")
    print("            NEXHUB DATABASE SEEDING SCRIPT          ")
    print("====================================================")
    print(f"Target backend: {BASE_URL}")
    print(f"Direct DB updates: {'enabled' if DIRECT_DB_UPDATES else 'disabled'}")
    print(f"SSL verification: {'disabled' if INSECURE_SSL else 'enabled'}")

    # 1. Register Users
    users_info = [
        {"username": "alice", "email": "alice@example.com", "password": "Password123", "streak": 5, "status": "active", "reputation": 100},
        {"username": "bob", "email": "bob@example.com", "password": "Password123", "streak": 0, "status": "active", "reputation": 0},
        {"username": "charlie", "email": "charlie@example.com", "password": "Password123", "streak": 8, "status": "active", "reputation": 35},
        {"username": "david", "email": "david@example.com", "password": "Password123", "streak": 2, "status": "active", "reputation": 50},
        {"username": "elena", "email": "elena@example.com", "password": "Password123", "streak": 12, "status": "active", "reputation": 80},
        {"username": "frank", "email": "frank@example.com", "password": "Password123", "streak": 0, "status": "deactivated", "reputation": 0},
        {"username": "grace", "email": "grace@example.com", "password": "Password123", "streak": 45, "status": "active", "reputation": 150},
        {"username": "hector", "email": "hector@example.com", "password": "Password123", "streak": 3, "status": "deactivated", "reputation": 20},
        {"username": "ivan", "email": "ivan@example.com", "password": "Password123", "streak": 15, "status": "active", "reputation": 15},
        {"username": "julia", "email": "julia@example.com", "password": "Password123", "streak": 22, "status": "active", "reputation": 65},
        {"username": "kevin", "email": "kevin@example.com", "password": "Password123", "streak": 17, "status": "deactivated", "reputation": 40},
        {"username": "laura", "email": "laura@example.com", "password": "Password123", "streak": 84, "status": "deactivated", "reputation": 95},
        {"username": "mike", "email": "mike@example.com", "password": "Password123", "streak": 0, "status": "active", "reputation": 0},
        {"username": "nancy", "email": "nancy@example.com", "password": "Password123", "streak": 1, "status": "active", "reputation": 30},
        {"username": "oscar", "email": "oscar@example.com", "password": "Password123", "streak": 30, "status": "active", "reputation": 75}
    ]
    
    for u in users_info:
        signup_user(u["username"], u["email"], u["password"])
        
    print("\n----------------------------------------------------")
    # Temporarily activate all users to ensure logins succeed on subsequent runs
    activate_all_users_in_db()

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
    # 3. Update User database-only fields (Streaks, Reputation, Status)
    for u in users_info:
        update_user_db_fields(u["username"], u["streak"], u["status"], u["reputation"])

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
        reward_amount=250000.00,
        reward_currency=TASK_CURRENCY,
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
        reward_amount=150000.00,
        reward_currency=TASK_CURRENCY,
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
        reward_amount=180000.00,
        reward_currency=TASK_CURRENCY,
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
        reward_amount=300000.00,
        reward_currency=TASK_CURRENCY,
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
        reward_amount=100000.00,
        reward_currency=TASK_CURRENCY,
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

    # 4. Ivan's Submission (Spam Rejection - resets streak and penalizes reputation by 25)
    aivan_id = assign_task(tokens["ivan"], t5_id, ids["ivan"], "ivan")
    if aivan_id:
        sub_ivan = submit_solution(tokens["ivan"], aivan_id, "https://github.com/grace/pipeline/pull/120")
        if sub_ivan:
            review_submission(tokens["grace"], sub_ivan, "REJECTED", "Spam deliverable. Only dummy text was provided in the PR description.", ids["grace"], rejection_reason="SPAM_OR_LOW_EFFORT")

    # 5. Seed new features (Reputation Gates, Deadlines, Attempt Exhaustion)
    print("\nSeeding new Quality Control & Deadline scenarios...")

    # A. Task with high reputation gate (minReputation = 60)
    t7_id = create_task(
        token=tokens["alice"],
        project_id=p1_id,
        title="Refactor State Management to Redux Toolkit",
        description="Migrate legacy React Native context providers to Redux Toolkit slice architecture to support global query caching.",
        deliverables="Redux store setup, converted login/profile slice states, and clean async Thunk integrations.",
        reward_amount=220000.00,
        reward_currency=TASK_CURRENCY,
        deadline_days_ahead=15,
        max_attempts=3,
        skills=["react-native", "redux", "typescript"],
        min_reputation=60
    )

    # B. Expired Task (deadline is in the past, claimed by Nancy)
    t8_id = create_task(
        token=tokens["grace"],
        project_id=p3_id,
        title="Legacy Docker Compose Cleanup",
        description="Cleanup deprecated environment scripts and migrate secrets to docker compose configurations.",
        deliverables="Clean docker-compose file with environment variables.",
        reward_amount=90000.00,
        reward_currency=TASK_CURRENCY,
        deadline_days_ahead=-3, # Overdue task!
        max_attempts=2,
        skills=["docker", "bash"]
    )
    # Claim expired task (to test scheduler expiration)
    assign_task(tokens["nancy"], t8_id, ids["nancy"], "nancy")

    # C. Max Attempts Exhaustion scenario
    t9_id = create_task(
        token=tokens["alice"],
        project_id=p2_id,
        title="Add SpotBugs static analysis tool",
        description="Configure SpotBugs inside gradle.build to automatically check local compilation builds for common security flaws.",
        deliverables="build.gradle configs and reports showing successful lint analysis builds.",
        reward_amount=120000.00,
        reward_currency=TASK_CURRENCY,
        deadline_days_ahead=12,
        max_attempts=2, # Max attempts = 2
        skills=["java", "gradle"]
    )
    # Hector claims it
    ahector_id = assign_task(tokens["hector"], t9_id, ids["hector"], "hector")
    if ahector_id:
        # Attempt 1: Submit and reject
        sub_h1 = submit_solution(tokens["hector"], ahector_id, "https://github.com/nexhub/ai-auditor/pull/12")
        if sub_h1:
            review_submission(tokens["alice"], sub_h1, "REJECTED", "Analysis configs are fine, but compile errors are thrown when running gradle check. Please fix compiler errors.", ids["alice"])
        # Attempt 2: Submit and reject again (this will trigger failed status automatically)
        sub_h2 = submit_solution(tokens["hector"], ahector_id, "https://github.com/nexhub/ai-auditor/pull/15")
        if sub_h2:
            review_submission(tokens["alice"], sub_h2, "REJECTED", "Compile errors persist. Max attempts are reached, task is released/failed.", ids["alice"])

    # D. Seed Collaborative Task scenario
    print("\nSeeding collaborative task scenario...")
    t10_id = create_task(
        token=tokens["alice"],
        project_id=p1_id,
        title="Develop CI/CD GitHub Actions Pipeline",
        description="Configure a robust GitHub Actions workflow that runs linting, tests, and builds the React Native application automatically on pull requests.",
        deliverables="github workflow yml config file and successful action run logs.",
        reward_amount=200000.00,
        reward_currency=TASK_CURRENCY,
        deadline_days_ahead=25,
        max_attempts=3,
        skills=["github-actions", "ci-cd", "yaml"],
        min_reputation=-500, # Open to all devs including negative reps
        collaborative=True
    )
    if t10_id:
        # David assigns himself to the collaborative task (Team 1)
        assign_task(tokens["david"], t10_id, ids["david"], "david")
        # Julia assigns herself to the collaborative task (Team 2)
        assign_task(tokens["julia"], t10_id, ids["julia"], "julia")

    seed_marketplace_catalog(tokens, ids)
    normalize_existing_task_currencies(tokens["alice"])

    print("\n====================================================")
    print("          SEEDING COMPLETED SUCCESSFULLY!           ")
    print("====================================================")

if __name__ == "__main__":
    main()
