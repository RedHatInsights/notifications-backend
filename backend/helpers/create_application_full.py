import helpers

base_url = "http://localhost:8085"
tp_part = ""  # set to /api/notifications if going via TurnPike

helpers.set_path_prefix(base_url + tp_part)

# Parameters to set
bundle_name = "a-bundle"
bundle_description = "My Bundle"
app_name = "my-app"
app_display_name = "My application"
event_type = "et1"
event_type_display_name = "First Event Type"
bg_name = "A BG"
account_id = "54321"

f = open("rhid.txt", "r")

line = f.readline()
# strip eventual \n at the end
x_rh_id = line.strip()
f.close()

# ---
# Add the application

print(">>> create bundle")
bundle_id = helpers.add_bundle(bundle_name, bundle_description)

print(">>> create application")
app_id = helpers.add_application(bundle_id, app_name, app_display_name)

print(">>> add eventType to application")
et_id = helpers.add_event_type(app_id, event_type, event_type_display_name)

print(">>> create a behavior group")
bg_id = helpers.create_behavior_group(bg_name, bundle_id, account_id, x_rh_id )

print(">>> add event type to behavior group")
helpers.add_event_type_to_behavior_group(et_id, bg_id, x_rh_id)

print(">>> create endpoint")
props = {
    "url": "http://localhost:8085",
    "method": "PUT",
    "secret_token": "bla-token",
    "type": "webhook"
}
ep_id = helpers.create_endpoint("bla-webhook", x_rh_id, props)

print(">>> link endpoint to behaviour group ")
helpers.link_bg_endpoint(bg_id, ep_id, x_rh_id)

print(">>> create camel endpoint")
props = {
    "url": "http://does-not-matter",
    "sub_type": "demo-log",
    "extras": {
        "mode": "random",
        "channel": "myLogger"
    }
}
ep_id = helpers.create_endpoint("demo-log", x_rh_id, props, "camel")

print(">>> link camel endpoint to behaviour group")
helpers.link_bg_endpoint(bg_id, ep_id, x_rh_id)


