#!/bin/bash

# Generate a unique client ID
CLIENT_ID=$(cat /proc/sys/kernel/random/uuid)
BASE_URL="${1%/queue}" # Remove /queue from the end if it exists
BASE_URL="${BASE_URL%/}" # Remove trailing / if it exists

if [ -z "$1" ]; then
    echo "Usage: ./subscribe.sh <BASE_URL>"
    echo "Example: ./subscribe.sh http://localhost:80"
    exit 1
fi

echo "Connecting to $BASE_URL with client ID: $CLIENT_ID"

# Function to send acknowledgment
send_ack() {
    local message_id=$1
    local ack_response=$(curl -s -X POST "$BASE_URL/queue/ack/$message_id?clientId=$CLIENT_ID")

    # Try to parse the response as JSON
    if echo "$ack_response" | jq -e . >/dev/null 2>&1; then
        local ack_id=$(echo "$ack_response" | jq -r '.id')
        local ack_content=$(echo "$ack_response" | jq -r '.content')
        local ack_processed=$(echo "$ack_response" | jq -r '.processed')
        echo "Acknowledged message - ID: $ack_id, Content: $ack_content, Processed: $ack_processed"
    else
        echo "Acknowledgment response: $ack_response"
    fi
}

# Function to process events
process_event() {
    local data=$1

    # Check if it's a ping
    if [ "$data" = "ping" ]; then
        echo "Received: ping"
        return
    fi

    # Try to parse JSON
    if echo "$data" | jq -e . >/dev/null 2>&1; then
        echo "Received: $(echo "$data" | jq .)"

        # Extract message ID and content
        local msg_id=$(echo "$data" | jq -r '.id // empty')
        local content=$(echo "$data" | jq -r '.content // empty')

        if [ ! -z "$msg_id" ] && [ ! -z "$content" ]; then
            send_ack "$msg_id"
        fi
    else
        echo "Received: $data"
    fi
}

# Main loop
while true; do
    echo "Connecting to server..."

    # Use curl for SSE streaming
    curl -N -s "$BASE_URL/queue/subscribe?clientId=$CLIENT_ID" | while read -r line; do
        if [[ $line == data:* ]]; then
            data="${line#data:}"
            data="${data#"${data%%[![:space:]]*}"}" # trim spaces
            process_event "$data"
        fi
    done

    # If connection drops
    echo "Connection lost. Attempting to reconnect in 5 seconds..."
    sleep 5
done