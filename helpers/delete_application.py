import helpers

base_url = "http://localhost:8085"
tp_part = ""  # set to /api/notifications if going via TurnPike

helpers.set_path_prefix(base_url + tp_part)

# Parameters to set
bundle_name = "a-bundle"
app_name = "my-app"

bundle_id = helpers.find_bundle(bundle_name)

if bundle_id is not None:
    app_id = helpers.find_application(bundle_id, app_name)
    if app_id is not None:
        helpers.delete_application(app_id)
    else:
        print("Application did not exist")
else:
    print("Bundle did not exist")
