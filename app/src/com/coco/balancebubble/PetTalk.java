package com.coco.balancebubble;

import java.util.Random;

/**
 * 一张图文案，附带它说出口时角色要做的动作。
 *
 * <p>语料和动作写在一起，改文案时顺手就能改动作，不会出现「文案改了动作没跟上」。
 */
public final class PetTalk {

    /** 一句台词。 */
    public static final class Line {
        public final String text;
        public final PetAction action;

        Line(String text, PetAction action) {
            this.text = text;
            this.action = action;
        }
    }

    private static final Line[] LINES = {
            new Line("今天也要加油鸭！", PetAction.BOUNCE),
            new Line("你在忙什么呀？", PetAction.LEAN),
            new Line("摸摸头～", PetAction.DUCK),
            new Line("呜哇！吓到了吗？", PetAction.POP),
            new Line("休息一下吧～", PetAction.SLEEPY),
            new Line("我一直在这里哦。", PetAction.FLOAT),
            new Line("要不要喝口水？", PetAction.LEAN),
            new Line("海浪的声音好好听。", PetAction.SWAY),
            new Line("困了…睡一会儿…", PetAction.SLEEPY),
            new Line("记账记得看看哦！", PetAction.NOD),
            new Line("一起去看海吧？", PetAction.FLOAT),
            new Line("今天过得怎么样？", PetAction.LEAN),
            new Line("诶嘿～", PetAction.BOUNCE),
            new Line("我转一圈给你看！", PetAction.SPIN),
            new Line("别熬太晚啦。", PetAction.SHAKE),
            new Line("有好好吃饭吗？", PetAction.LEAN),
            new Line("我在水面漂着呢～", PetAction.FLOAT),
            new Line("要不要抱一下？", PetAction.STRETCH),
            new Line("嘿嘿，被发现了。", PetAction.POP),
            new Line("今天的花销记了吗？", PetAction.NOD),
            new Line("呜…有点冷。", PetAction.DUCK),
            new Line("慢慢来就好。", PetAction.SWAY),
            new Line("我超喜欢你的！", PetAction.BOUNCE),
            new Line("又见面啦～", PetAction.POP),
            new Line("尾巴摇摇～", PetAction.WAG),
            new Line("挥挥小手～", PetAction.WAVE),
    };

    private static final Random RANDOM = new Random();
    private static int lastIndex = -1;

    private PetTalk() {
    }

    /** 全部台词（测试用）。 */
    public static Line[] all() {
        return LINES.clone();
    }

    public static int size() {
        return LINES.length;
    }

    /** 随机一句，尽量避免和上一句重复。 */
    public static Line random() {
        if (LINES.length == 0) return null;
        int i = RANDOM.nextInt(LINES.length);
        if (LINES.length > 1 && i == lastIndex) {
            i = (i + 1 + RANDOM.nextInt(LINES.length - 2)) % LINES.length;
        }
        lastIndex = i;
        return LINES[i];
    }
}
