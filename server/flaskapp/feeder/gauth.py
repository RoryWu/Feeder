# -*- coding: utf-8 -*-
"""
Handles validating client bearer tokens, or
that the user/password is correct.
"""
from flask import request, abort, current_app
from httplib2 import Http
import json
import base64
from .models import get_user
from werkzeug.security import check_password_hash


# This is not the salt used to store passwords
# This is only the salt used by the android client
# Used when setting up new users
__ANDROID_SALT__ = b'4fb3a4355d7bfed240015f8e51e7b42f3455c17e'


def validate_token(access_token):
    '''Verifies that an access-token is valid and
    meant for this app.

    Returns None on fail, and an e-mail on success'''
    h = Http()
    resp, cont = h.request("https://www.googleapis.com/oauth2/v2/userinfo",
                           headers={'Host': 'www.googleapis.com',
                                    'Authorization': access_token})

    if not resp['status'] == '200':
        return None

    try:
        data = json.loads(cont)
    except TypeError:
        # httplib2 returns byte objects
        data = json.loads(cont.decode())

    return data['email']


def validate_userpass(credentials):
    '''
    Validate the username/password provided.

    Returns None failure, and a username on success
    '''
    # Get the BASE64 encoded username:password
    enc_userpass = credentials.replace('Basic ', '')
    # Decode BASE64 from bytes string
    userpass = base64.b64decode(enc_userpass.encode('UTF-8'))
    # Need to change back to String
    userpass = userpass.decode('UTF-8')

    try:
        username, password = userpass.split(':')
        # Enforce lowercase on password hash
        password = password.lower()
        # Check validity of username password
        user = get_user(username, allow_creation=False)
        if user is None or user.passwordhash is None:
            # User does not exist or has no password
            return None

        # Check password validity for existing user
        if check_password_hash(user.passwordhash, password):
            # Valid!
            return username
    except:
        # invalid user/pass
        return None

    # Must be invalid...
    return None


def is_basic(credentials):
    '''Returns True if credentials are of username:password type'''
    return credentials.startswith('Basic ')


def authorized(fn):
    """Decorator that checks that requests
    contain an id-token in the request header.
    userid will be None if the
    authentication failed, and have an id otherwise.

    Usage:
    @app.route("/")
    @authorized
    def secured_root(userid=None):
        pass
    """

    def _wrap(*args, **kwargs):
        if 'Authorization' not in request.headers:
            # Unauthorized
            abort(401)
            return None

        userid = None
        credentials = request.headers['Authorization']
        if (is_basic(credentials) and
                current_app.config['FEEDER_ALLOW_USERPASS']):
            print("Checking user...")
            userid = validate_userpass(credentials)
        elif (not is_basic(credentials) and
                current_app.config['FEEDER_ALLOW_GOOGLE']):
            print("Checking token...")
            userid = validate_token(credentials)

        if userid is None:
            # Unauthorized
            abort(401)
            return None

        return fn(userid=userid, *args, **kwargs)
    return _wrap
