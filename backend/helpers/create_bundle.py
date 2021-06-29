import helpers

base_url = "http://localhost:8085"
tp_part = ""  # set to /api/notifications if going via TurnPike

helpers.set_path_prefix(base_url + tp_part)

# Parameters to set
bundle_name = "a-bundle"
bundle_description = "My Bundle"


print(">>> create bundle")
bundle_id = helpers.add_bundle(bundle_name, bundle_description)

