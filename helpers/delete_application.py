import helpers

base_url = "http://localhost:8085"
tp_part = ""  # set to /api/notifications if going via TurnPike

helpers.set_path_prefix(base_url + tp_part)

# Parameters to set
bundle_name = "a-bundle"
app_name = "my-app"

app_id = helpers.find_application(app_name, bundle_name)

if app_id is not None:
    helpers.delete_application(app_id)
else:
    print("Application did not exist")
