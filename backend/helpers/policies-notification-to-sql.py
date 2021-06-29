'''
Helper script to load a csv file (with headers) from stdin with the contents of the email-subscriptions table from
policies-notification.
This script will output sql queries to insert the subscriptions to notification-backend, create email endpoints for
each account, delete endpoints from the endpoint_targets of the accounts and insert the newly created endpoint to
endpoint_target under the event_type provided as the first argument.

Usage:
cat subs.cvs | python policies-notification-to-sql.py id-of-policy-triggered-event-type
'''
import sys
import uuid
import csv

app = 'policies'
bundle = 'insights'

if len(sys.argv) < 2:
    raise Exception('Specify the event_type_id of policy-triggered')

event_type_id = sys.argv[1]

reader = csv.DictReader(sys.stdin)
account_ids = set()
for row in reader:
    policies_event_type = row['event_type']
    if policies_event_type == 'policies-instant-mail':
        event_type = 'INSTANT'
    elif policies_event_type == 'policies-daily-mail':
        event_type = 'DAILY'
    else:
        raise Exception(f"Invalid policies_event_type {policies_event_type}")

    account_ids.add(row['account_id'])
    print(f"INSERT INTO endpoint_email_subscriptions(account_id, user_id, subscription_type, application, bundle) VALUES ('{row['account_id']}', '{row['user_id']}', '{event_type}', '{app}', '{bundle}');")

for account_id in account_ids:
    print(f"DELETE FROM endpoint_targets WHERE account_id='{account_id}';")
    endpoint_id = uuid.uuid4()
    print(f"INSERT INTO endpoints(id, account_id, endpoint_type, enabled, name, description) VALUES('{endpoint_id}', '{account_id}', 1, true, 'email-endpoint', '');")
    print(f"INSERT INTO endpoint_targets(account_id, endpoint_id, event_type_id) VALUES('{account_id}', '{endpoint_id}', '{event_type_id}');")
