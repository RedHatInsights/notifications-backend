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

print(">>> add endpoint")
ep_id = helpers.create_endpoint("bla", x_rh_id)

print(">>> add endpoint to event type")
helpers.add_endpoint_to_event_type(et_id, ep_id, x_rh_id)
