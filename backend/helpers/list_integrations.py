import helpers

base_url = "http://localhost:8085"
tp_part = ""  # set to /api/notifications if going via TurnPike

helpers.set_path_prefix(base_url + tp_part)


####

f = open("rhid.txt", "r")

line = f.readline()
# strip eventual \n at the end
x_rh_id = line.strip()
f.close()

endpoints = helpers.list_endpoints(x_rh_id)
if not endpoints:
    print("No endpoints found")
    exit(0)

print("Id                                   name  type subtype enabled status processorId")
for endpoint in endpoints:
    p_id = "n/a"
    if endpoint["sub_type"] == "slack":
        props = endpoint["properties"]
        extras = props["extras"]
        if "processorId" in extras:
            p_id = extras["processorId"]

    print(endpoint["id"],
          endpoint["name"],
          endpoint["type"],
          endpoint["sub_type"],
          endpoint["enabled"],
          endpoint["status"],
          p_id
          )
