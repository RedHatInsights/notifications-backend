import requests


def set_path_prefix(base_path):
    global applications_prefix
    global bundles_prefix
    applications_prefix = base_path + "/internal/applications"
    bundles_prefix = base_path + "/internal/bundles"


def find_application(app_name, bundle_name):
    """Find an application by name and return its UUID or return None
    :param name: Name of the application
    """
    r = requests.get(applications_prefix + "?bundleName=" + bundle_name)
    if r.status_code != 200:
        return None

    j = r.json()
    for app in j:
        if app["name"] == app_name:
            return app["id"]

    return None


def add_application(name, display_name, bundle_name):
    """Adds an application if it does not yet exist
    :param bundle_name: name of the bundle we add the application to
    :param name: Name of the application, [a-z0-9-]+
    :param display_name: Display name of the application
    """

    # First try to find it.
    ret = find_application(name,bundle_name)
    if ret is not None:
        return ret

    bundle_id = find_bundle(bundle_name)

    # The app does not yet exist, so try to create
    app_json = {"name": name,
                "display_name": display_name,
                "bundle_id": bundle_id}

    r = requests.post(applications_prefix, json=app_json)
    print(r.status_code)
    response_json = r.json()
    print(response_json)
    if r.status_code / 10 != 20:
        exit(1)
    aid = response_json['id']
    return aid


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

    et_json = {"name": name, "display_name": display_name}
    r = requests.post(applications_prefix + "/" + application_id + "/eventTypes",
                      json=et_json)
    if r.status_code / 10 != 20:
        exit(2)
    response_json = r.json()
    print(response_json)


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

    r = requests.post(bundles_prefix, json=bundle_json)
    print(r.status_code)
    response_json = r.json()
    print(response_json)
    if r.status_code / 10 != 20:
        exit(1)
    aid = response_json['id']
    return aid


def find_bundle(name):
    """Find an application by name and return its UUID or return None
    :param name: Name of the application
    """
    r = requests.get(bundles_prefix)
    if r.status_code != 200:
        return None

    j = r.json()
    for bundle in j:
        if bundle["name"] == name:
            return bundle["id"]

    return None


def find_event_type(application_id, name):
    """Find an event type by name for an application"""
    r = requests.get(applications_prefix + "/" + application_id + "/eventTypes/")

    if r.status_code != 200:
        return None

    j = r.json()
    for et in j:
        if et["name"] == name:
            return et["id"]

    return None

