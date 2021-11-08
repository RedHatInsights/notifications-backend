import helpers
import sys

base_url = "http://localhost:8085"
tp_part = ""  # set to /api/notifications if going via TurnPike

helpers.set_path_prefix(base_url + tp_part)

# Parameters to set
bundle_name = "a-bundle"
app_name = "my-app"

f = open("rhid.txt", "r")

line = f.readline()
# strip eventual \n at the end
x_rh_id = line.strip()
f.close()

if len(sys.argv) != 2:
    exit("No event type name given, please provide it as argument")

et_name = sys.argv[1]

bundle_id = helpers.find_bundle(bundle_name)
app_id = helpers.find_application(bundle_id, app_name)
et = helpers.find_event_type(app_id, et_name)
if et is None:
    exit(f"No event type with name {et_name} found for {bundle_name}->{app_name}")

helpers.print_history_for_event_type(bundle_id, app_id, et['display_name'], x_rh_id)


