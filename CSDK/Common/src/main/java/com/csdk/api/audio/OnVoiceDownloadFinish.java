package com.csdk.api.audio;

/**
 * Create LuckMerlin
 * Date 19:46 2020/10/15
 * TODO
 */
public interface OnVoiceDownloadFinish {
    void onVoiceDownloadFinish(boolean succeed, String note, String cloudPath, String localPath);
}
