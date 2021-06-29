import helpers

base_url = "http://localhost:8085"
tp_part = ""  # set to /api/notifications if going via TurnPike

helpers.set_path_prefix(base_url + tp_part)

# Parameters to set
bundle_name = "a-bundle"

bundle_id = helpers.find_bundle(bundle_name)

if bundle_id is not None:
    print("Bundle has id " + bundle_id)

    helpers.delete_bundle(bundle_id)

    bid = helpers.find_bundle(bundle_name)
    if bid is not None:
        print("Bundle is not gone")
        exit(1)
else:
    print("Bundle did not exist")
