import requests
import helpers

base_url = "http://localhost:8085"
tp_part = ""  # set to /api/notifications if going via TurnPike

helpers.set_path_prefix(base_url + tp_part)

# Parameters to set
bundle_name = "ABundle"
bundle_description = "My Bundle"
app_name = "my-app"
app_display_name = "My application"
event_type = "ET1"
event_type_display_name = "First Event Type"



# ---
# Add the application

print(">>> create bundle")
bundle_id = helpers.add_bundle("my-bundle", "My Bundle")

print(">>> create application")
app_id = helpers.add_application(app_name, app_display_name, bundle_id)

print(">>> add eventType to application")
helpers.add_event_type(app_id, event_type, event_type_display_name)
