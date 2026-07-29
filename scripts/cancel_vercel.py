import os
import sys
import json
import requests
from datetime import datetime

def get_vercel_token():
    # 1. Check environment variable
    token = os.environ.get("VERCEL_TOKEN")
    if token:
        print("[INFO] Using Vercel Token from VERCEL_TOKEN environment variable.")
        return token

    # 2. Prompt user
    print("=" * 60)
    print(" VERCEL DEPLOYMENT CANCELLATION UTILITY")
    print("=" * 60)
    print("To cancel a Vercel deployment, you need a Vercel Access Token.")
    print("You can generate one here: https://vercel.com/account/tokens")
    print("-" * 60)
    token = input("Please enter your Vercel Access Token: ").strip()
    if not token:
        print("[ERROR] A Vercel Access Token is required.")
        sys.exit(1)
    return token

def get_team_id():
    team_id = os.environ.get("VERCEL_TEAM_ID")
    if team_id:
        print(f"[INFO] Using Team ID from VERCEL_TEAM_ID: {team_id}")
        return team_id
    
    val = input("Enter Vercel Team ID (optional, press Enter if personal account): ").strip()
    return val if val else None

def list_and_cancel_deployments():
    token = get_vercel_token()
    team_id = get_team_id()
    
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    
    params = {
        "limit": 20
    }
    if team_id:
        params["teamId"] = team_id

    print("\n[INFO] Fetching deployments from Vercel...")
    url = "https://api.vercel.com/v7/deployments"
    try:
        response = requests.get(url, headers=headers, params=params)
    except Exception as e:
        print(f"[ERROR] Failed to make request to Vercel API: {e}")
        sys.exit(1)

    if response.status_code == 401:
        print("[ERROR] Unauthorized. Please verify your Vercel Access Token is valid.")
        sys.exit(1)
    elif response.status_code != 200:
        print(f"[ERROR] Failed to fetch deployments. API returned {response.status_code}: {response.text}")
        sys.exit(1)

    data = response.json()
    deployments = data.get("deployments", [])
    
    if not deployments:
        print("[INFO] No deployments found on this account/team.")
        return

    print("\n" + "=" * 80)
    print(f"{'INDEX':<6} | {'PROJECT':<20} | {'STATE':<10} | {'CREATED AT':<16} | {'DEPLOYMENT ID':<25}")
    print("-" * 80)
    
    active_deployments = []
    
    for i, dep in enumerate(deployments):
        dep_id = dep.get("uid")
        project = dep.get("name")
        state = dep.get("state")
        
        # Format date
        created_timestamp = dep.get("created")
        created_str = "Unknown"
        if created_timestamp:
            try:
                # Vercel returns timestamp in milliseconds
                dt = datetime.fromtimestamp(created_timestamp / 1000.0)
                created_str = dt.strftime("%Y-%m-%d %H:%M")
            except Exception:
                pass
        
        print(f"{i:<6} | {project:<20} | {state:<10} | {created_str:<16} | {dep_id:<25}")
        
        if state in ["BUILDING", "QUEUED", "INITIALIZING"]:
            active_deployments.append((i, dep_id, project, state))
            
    print("=" * 80)
    
    if active_deployments:
        print(f"\n[!] Found {len(active_deployments)} active (BUILDING/QUEUED/INITIALIZING) deployment(s):")
        for i, dep_id, project, state in active_deployments:
            print(f"  - [{i}] Project '{project}' (State: {state}, ID: {dep_id})")
            
        choice = input("\nWould you like to cancel all active deployments? (yes/no/index): ").strip().lower()
        
        to_cancel = []
        if choice in ["yes", "y"]:
            to_cancel = [dep[1] for dep in active_deployments]
        elif choice.isdigit():
            idx = int(choice)
            selected = [dep for dep in active_deployments if dep[0] == idx]
            if selected:
                to_cancel = [selected[0][1]]
            else:
                print("[ERROR] Invalid index selected.")
        else:
            print("[INFO] No deployments were selected for cancellation.")
            
        for dep_id in to_cancel:
            cancel_url = f"https://api.vercel.com/v12/deployments/{dep_id}/cancel"
            cancel_params = {}
            if team_id:
                cancel_params["teamId"] = team_id
                
            print(f"[INFO] Sending cancel request for deployment {dep_id}...")
            try:
                res = requests.patch(cancel_url, headers=headers, params=cancel_params)
                if res.status_code == 200:
                    print(f"[SUCCESS] Successfully cancelled deployment {dep_id}!")
                elif res.status_code == 400:
                    print(f"[WARNING] Deployment {dep_id} could not be cancelled. It may have already finished or failed. API response: {res.json().get('error', {}).get('message')}")
                else:
                    print(f"[ERROR] Failed to cancel deployment {dep_id}. HTTP {res.status_code}: {res.text}")
            except Exception as e:
                print(f"[ERROR] Exception occurred while cancelling {dep_id}: {e}")
    else:
        print("\n[INFO] No active (BUILDING/QUEUED/INITIALIZING) deployments found.")
        
        manual_id = input("\nIf you want to cancel a specific deployment by ID, enter it here (or press Enter to exit): ").strip()
        if manual_id:
            cancel_url = f"https://api.vercel.com/v12/deployments/{manual_id}/cancel"
            cancel_params = {}
            if team_id:
                cancel_params["teamId"] = team_id
            
            print(f"[INFO] Sending cancel request for deployment {manual_id}...")
            try:
                res = requests.patch(cancel_url, headers=headers, params=cancel_params)
                if res.status_code == 200:
                    print(f"[SUCCESS] Successfully cancelled deployment {manual_id}!")
                else:
                    print(f"[ERROR] Failed to cancel deployment {manual_id}. HTTP {res.status_code}: {res.text}")
            except Exception as e:
                print(f"[ERROR] Exception occurred: {e}")

if __name__ == "__main__":
    try:
        list_and_cancel_deployments()
    except KeyboardInterrupt:
        print("\n[INFO] Exiting...")
        sys.exit(0)
