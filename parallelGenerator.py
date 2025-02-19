import concurrent.futures
import json
import sys
import urllib.request
import urllib.error

def send_post_request(url, i):
    data = json.dumps({"content": f"Message {i}"}).encode('utf-8')
    headers = {
        "Content-Type": "application/json"
    }
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    
    try:
        with urllib.request.urlopen(req) as response:
            return f"Message {i} sent successfully. Response: {response.read().decode('utf-8')}"
    except urllib.error.URLError as e:
        return f"Failed to send message {i}. Error: {e.reason}"

def main(url, num_requests, max_workers):
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_id = {executor.submit(send_post_request, url, i): i for i in range(1, num_requests + 1)}
        for future in concurrent.futures.as_completed(future_to_id):
            id = future_to_id[future]
            try:
                result = future.result()
                print(result)
            except Exception as exc:
                print(f"Request {id} generated an exception: {exc}")

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python parallel_post.py <url> <number_of_requests> <max_workers>")
        sys.exit(1)
    
    url = sys.argv[1]
    num_requests = int(sys.argv[2])
    max_workers = int(sys.argv[3])
    main(url, num_requests, max_workers)
