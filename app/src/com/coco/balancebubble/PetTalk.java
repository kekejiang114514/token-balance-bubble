package com.coco.balancebubble;

import java.util.Random;

/**
 * 桌宠的卖萌语料库。
 *
 * <p>每句话都配了一个动作，说话时同步播放，这样角色是一边做动作一边说话，
 * 而不是干站着冒文字。句子控制在 12 个字以内，保证气泡最多折两行。
 */
public class PetTalk {

    private static final Random RANDOM = new Random();
    private static int lastIndex = -1;

    /** 一句话＋它的配套动作。 */
    public static class Line {
        public final String text;
        public final PetAction action;

        public Line(String text, PetAction action) {
            this.text = text;
            this.action = action;
        }
    }

    private static final Line[] LINES = {
            new Line("主人来啦～", PetAction.WAVE),
            new Line("咕噜咕噜…吐泡泡", PetAction.SWIM),
            new Line("今天也元气满满！", PetAction.HAPPY),
            new Line("摸摸头～", PetAction.NOD),
            new Line("在忙什么呀？", PetAction.LOOK),
            new Line("一直在等你哦", PetAction.LOOK),
            new Line("最喜欢主人了", PetAction.HAPPY),
            new Line("一起去看海吧？", PetAction.SWIM),
            new Line("陪我一会儿嘛～", PetAction.WAVE),
            new Line("记得喝水哦", PetAction.NOD),
            new Line("甩甩尾巴～", PetAction.SWIM),
            new Line("别老盯着屏幕", PetAction.SHAKE),
            new Line("给你好运 buff！", PetAction.HAPPY),
            new Line("被我抓到偷看啦", PetAction.SURPRISE),
            new Line("大海不如你好看", PetAction.HAPPY),
            new Line("辛苦啦，歇会儿", PetAction.NOD),
            new Line("这是我们的秘密", PetAction.NOD),
            new Line("哗啦哗啦～水花", PetAction.SWIM),
            new Line("累了就靠着我", PetAction.SLEEPY),
            new Line("想听我唱歌吗？", PetAction.WAVE),
            new Line("转圈圈…好晕呀", PetAction.SHAKE),
            new Line("你的心情最重要", PetAction.HAPPY),
            new Line("呜哇！吓到了吗？", PetAction.SURPRISE),
            new Line("陪你到天亮～", PetAction.SLEEPY),
            new Line("账单一读就跳起来", PetAction.JUMP),
            new Line("跳一下给你看", PetAction.JUMP),
    };

    /** 供外部（设置页预览、单测）读取全部语料。 */
    public static Line[] all() {
        return LINES.clone();
    }

    /** 随机取一条，不会和上一条重复。 */
    public static Line random() {
        if (LINES.length == 0) return new Line("", PetAction.IDLE);
        if (LINES.length == 1) return LINES[0];
        int i;
        do {
            i = RANDOM.nextInt(LINES.length);
        } while (i == lastIndex);
        lastIndex = i;
        return LINES[i];
    }
}
