package com.csdk.api.core;

/**
 * Create LuckMerlin
 * Date 11:30 2020/8/26
 * TODO
 */
public interface Event {
    int EVENT_FRIEND_LIST_CHANGED=-2001;
    int EVENT_MESSAGE_SENDING=-2002;
    int EVENT_MESSAGE_RECEIVED=-2003;
    /**
     * @deprecated
     */
    int EVENT_MESSAGE_UPDATE=-2004;
    int EVENT_LIVE_ROOM_USER_ADD=-2005;
    int EVENT_LIVE_ROOM_USER_REMOVED=-2006;
    int EVENT_MENU_LIST_CHANGED=-2007;
    int EVENT_LOGIN_CHANGED=-2008;
    int EVENT_LOGIN_FAIL=-20030;
    int EVENT_AUTH_FAIL=-20031;
    //Api
    int EVENT_LOGIN_SUCCEED=-20032;
    int EVENT_USER_CHANGED=-2009;
    int EVENT_MESSAGE_PENDING=-2010;
    int EVENT_FRIEND_CHAT_SESSION_UNREAD_MESSAGE_LIST_CHANGE=-2012;
    int EVENT_BLOCK_FRIEND_LIST_CHANGED=-2013;
    int EVENT_MESSAGE_SEND_RECEIPT=-20014;
//    int EVENT_ONLINE_STATUS_CHANGED=-20015;
    int EVENT_FRIEND_ADD_REQUEST_RECEIVE=-20016;
    int EVENT_FRIEND_ADD_REQUEST_LIST_CHANGE=-2011;
    int EVENT_MESSAGE_SENT=-20017;
    int EVENT_FORCE_LOGIN_OUT=-20018;
    int EVENT_MESSAGE_DELETE=-20019;
    int EVENT_INITIAL_FAIL=-20020;
}
