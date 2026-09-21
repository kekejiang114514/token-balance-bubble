package com.coco.balancebubble;

import java.util.Random;

/**
 * 桌宠模式的卖萌语料库。
 * 句子控制在 10 个字以内，保证一行能放进气泡、不撑破屏幕。
 */
public class PetTalk {

    private static final Random RANDOM = new Random();
    private static int lastIndex = -1;

    public static final String[] PHRASES = {
            "主人来啦～",
            "咕噜咕噜…吐泡泡",
            "今天也元气满满！",
            "摸摸头～",
            "在忙什么呀？",
            "一直在等你哦",
            "最喜欢主人了",
            "一起去看海吧？",
            "陪我一会儿嘛～",
            "记得喝水哦",
            "甩甩尾巴～",
            "别老盯着屏幕",
            "给你好运 buff！",
            "被我抓到偷看啦",
            "大海不如你好看",
            "辛苦啦，歇会儿",
            "这是我们的秘密",
            "哗啦哗啦～水花",
            "累了就靠着我",
            "想听我唱歌吗？",
            "转圈圈…好晕呀",
            "你的心情最重要",
            "呜哇！吓到了吗？",
            "陪你到天亮～",
    };

    /** 随机取一条，不会和上一条重复。 */
    public static String random() {
        if (PHRASES.length == 0) return "";
        if (PHRASES.length == 1) return PHRASES[0];
        int i;
        do {
            i = RANDOM.nextInt(PHRASES.length);
        } while (i == lastIndex);
        lastIndex = i;
        return PHRASES[i];
    }
}
