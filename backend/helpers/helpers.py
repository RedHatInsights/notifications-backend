"""Helper methods to talk with the notifications backend"""

import uuid
import requests


def set_path_prefix(base_path):
    """Set up the paths to use"""
    if base_path is None:
        raise RuntimeError("No base path passed")

    global __APPLICATION_PREFIX
    global __BUNDLES_PREFIX
    global event_types_prefix
    global integrations_prefix
    global notifications_prefix
    __APPLICATION_PREFIX = base_path + "/internal/applications"
    __BUNDLES_PREFIX = base_path + "/internal/bundles"
    event_types_prefix = base_path + "/internal/eventTypes"
    integrations_prefix = base_path + "/api/integrations/v1.0"
    notifications_prefix = base_path + "/api/notifications/v1.0"


def find_application(bundle_id, app_name):
    """Find an application by name and return its UUID or return None
    :param bundle_id Id of the bundle under which the app resides
    :param app_name: Name of the application
    """
    r = requests.get(__BUNDLES_PREFIX + "/" + bundle_id + "/applications")
    if r.status_code != 200:
        return None

    j = r.json()
    for app in j:
        if app["name"] == app_name:
            return app["id"]

    return None


def add_application(bundle_id, name, display_name):
    """Adds an application if it does not yet exist
    :param bundle_id: id of the bundle we add the application to
    :param name: Name of the application, [a-z0-9-]+
    :param display_name: Display name of the application
    """

    # First try to find it.
    ret = find_application(bundle_id, name)
    if ret is not None:
        return ret

    # The app does not yet exist, so try to create
    app_json = {"name": name,
                "display_name": display_name,
                "bundle_id": bundle_id}

    r = requests.post(__APPLICATION_PREFIX, json=app_json)
    print(r.status_code)
    response_json = r.json()
    print(response_json)
    if r.status_code / 10 != 20:
        exit(1)
    aid = response_json['id']
    return aid


def delete_application(app_id):
    """Deletes an application by its id"""

    r = requests.delete(__APPLICATION_PREFIX + "/" + app_id)
    print(r.status_code)


def delete_bundle(bundle_id):
    """Deletes a bundle by its id"""
    r = requests.delete(__BUNDLES_PREFIX + "/" + bundle_id)
    print(r.status_code)


def add_event_type(application_id, name, display_name):
    """Add an EventType by name
    :param application_id: UUID of the application
    :param name: Name of the type
    :param display_name: Display name of the type
    """

    # First try to find it
    ret = find_event_type(application_id, name)
    if ret is not None:
        return ret

    # It does not exist, so create it

    et_json = {"name": name, "display_name": display_name, "application_id": application_id}
    r = requests.post(event_types_prefix, json=et_json)
    response_json = r.json()
    print(response_json)
    if r.status_code / 10 != 20:
        exit(2)

    return response_json['id']


def add_bundle(name, display_name):
    """Adds a bundle if it does not yet exist
    :param name: Name of the bundle, [a-z0-9-]+
    :param display_name: Display name of the application
    """

    # First try to find it.
    ret = find_bundle(name)
    if ret is not None:
        return ret

    # It does not yet exist, so try to create
    bundle_json = {"name": name,
                   "display_name": display_name}

    r = requests.post(__BUNDLES_PREFIX, json=bundle_json)
    print(r.status_code)
    response_json = r.json()
    print(response_json)
    if r.status_code / 10 != 20:
        exit(1)
    aid = response_json['id']
    return aid


def find_bundle(name):
    """Find a bundle by name and return its UUID or return None
    :param name: Name of the bundle
    """
    result = requests.get(__BUNDLES_PREFIX)
    if result.status_code != 200:
        return None

    result_json = result.json()
    for bundle in result_json:
        if bundle["name"] == name:
            return bundle["id"]

    return None


def find_event_type(application_id, name):
    """Find an event type by name for an application.
    Returns the full type or None if not found
    """
    r = requests.get(__APPLICATION_PREFIX + "/" + application_id + "/eventTypes")

    if r.status_code != 200:
        return None

    j = r.json()
    for et in j:
        if et["name"] == name:
            return et

    return None


def create_endpoint(name, xrhid, properties, ep_type="webhook"):
    """Creates an endpoint"""

    ep_uuid = uuid.uuid4()
    ep_id = str(ep_uuid)
    properties["endpointId"] = ep_id
    ep_json = {"name": name,
               "description": name,
               "enabled": True,
               "properties": properties,
               "type": ep_type}

    h = {"x-rh-identity": xrhid}

    r = requests.post(integrations_prefix + "/endpoints", json=ep_json, headers=h)
    print(r.status_code)
    if r.status_code / 100 != 2:
        print(r.reason)
        exit(1)

    response_json = r.json()
    epid = response_json["id"]
    print(epid)

    return epid


def find_behavior_group(display_name, bundle_id, x_rhid):
    """Find a behavior group by its display name"""

    headers = {"x-rh-identity": x_rhid}
    r = requests.get(notifications_prefix + "/notifications/bundles/" + bundle_id + "/behaviorGroups",
                     headers=headers)

    if r.status_code != 200:
        return None

    j = r.json()
    for bg in j:
        if bg["display_name"] == display_name:
            return bg["id"]

    return None


def create_behavior_group(name, bundle_id, x_rhid):
    """Creates a behavior group"""

    bg_id = find_behavior_group(name, bundle_id, x_rhid)
    if bg_id is not None:
        return bg_id

    bg_json = {"display_name": name,
               "bundle_id": bundle_id}

    headers = {"x-rh-identity": x_rhid}
    r = requests.post(notifications_prefix + "/notifications/behaviorGroups",
                      json=bg_json,
                      headers=headers)

    print(r.status_code)
    if r.status_code / 100 != 2:
        print(r.reason)
        exit(1)

    response_json = r.json()
    bg_id = response_json["id"]
    print(bg_id)

    return bg_id


def link_bg_endpoint(bg_id, ep_id, x_rhid):
    """Link the behavior group to the endpoint"""
    headers = {"x-rh-identity": x_rhid}

    ep_list = [ep_id]

    r = requests.put(notifications_prefix + "/notifications/behaviorGroups/" + bg_id + "/actions",
                     json=ep_list,
                     headers=headers)


def add_endpoint_to_event_type(event_type_id, endpoint_id, x_rhid):
    headers = {"x-rh-identity": x_rhid}
    r = requests.put(notifications_prefix + "/notifications/eventTypes/" + event_type_id + "/" + endpoint_id,
                     headers=headers)

    print(r.status_code)


def shorten_path(path):
    """Shorten an incoming domain name like path to
       only have the first char of each segment except the last
       e.g. foo.bar.baz -> f.b.baz
     """
    out = ""
    segments = path.split(".")
    l = len(segments)
    i = 0
    while i < l:
        element = segments[i]
        if i < l-1:
            out = out + element[0]
            out = out + "."
        else:
            out = out + element
        i += 1

    return out


def print_history_for_event_type(bundle_id, app_id, event_type_name, x_rhid):
    headers = {"x-rh-identity": x_rhid}
    params={"bundleIds": bundle_id,
            "appIds": app_id,
            "includeDetails": True,
            "eventTypeDisplayName": event_type_name}
    r = requests.get(notifications_prefix + "/notifications/events/",
                     params=params,
                     headers=headers)

    if r.status_code != 200:
        print (r.reason)
        exit(1)

    response_json = r.json()
    data = response_json['data']
    for entry in data:
        print("Entry created at " + entry["created"] )

        for action in entry["actions"]:
            print(f"  Type  {action['endpoint_type']}, success= {action['invocation_result']}")
            if action['endpoint_type'] == 'camel':
                details = action['details']
                if details is None:
                    print("    No details provided")
                else:
                    print("    sub_type   " + shorten_path(details['type']))
                    print("    target url " + details['target'])
                    print("    outcome    " + details['outcome'])


def add_event_type_to_behavior_group(et_id, bg_id, x_rh_id):
    bg_set = [bg_id]

    headers = {"x-rh-identity": x_rh_id}
    r = requests.put(notifications_prefix + "/notifications/eventTypes/" + et_id + "/behaviorGroups",
                     json=bg_set,
                     headers=headers)

    print(r.status_code)

    return None
