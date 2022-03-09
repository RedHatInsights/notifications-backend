import helpers

base_url = "http://localhost:8085"
tp_part = ""  # set to /api/notifications if going via TurnPike

helpers.set_path_prefix(base_url + tp_part)

# Parameters to set
endpoint_name = "slack"

####

f = open("rhid.txt", "r")

line = f.readline()
# strip eventual \n at the end
x_rh_id = line.strip()
f.close()

helpers.delete_endpoint(endpoint_name, x_rh_id)
