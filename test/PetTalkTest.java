import com.coco.balancebubble.PetAction;
import com.coco.balancebubble.PetTalk;

import java.util.Arrays;
import java.util.HashSet;

/** JVM 单测：卖萌语料与动作的配对关系。 */
public class PetTalkTest {
    static int fail = 0;

    static void eq(String what, Object a, Object b) {
        boolean ok = a == null ? b == null : a.equals(b);
        if (!ok) {
            fail++;
            System.out.println("  FAIL " + what + " 期望=" + b + " 实际=" + a);
        }
    }

    static void ok(String what, boolean cond) {
        if (!cond) {
            fail++;
            System.out.println("  FAIL " + what);
        }
    }

    public static void main(String[] args) {
        PetTalk.Line[] all = PetTalk.all();

        System.out.println("== 语料 ==");
        ok("语料不少于 20 条（当前 " + all.length + "）", all.length >= 20);
        HashSet<String> seen = new HashSet<String>();
        for (PetTalk.Line l : all) {
            ok("句子非空", l.text != null && l.text.length() > 0);
            ok("句子不超过 12 字：「" + l.text + "」", l.text.length() <= 12);
            ok("每句都配了动作：「" + l.text + "」", l.action != null && l.action != PetAction.IDLE);
            seen.add(l.text);
            System.out.printf("  %-14s → %s%n", l.text, l.action);
        }
        eq("没有重复句子", seen.size(), all.length);

        System.out.println("== 随机 ==");
        int last = -1;
        boolean repeat = false;
        for (int i = 0; i < 3000; i++) {
            int idx = Arrays.asList(all).indexOf(PetTalk.random());
            if (idx == last) repeat = true;
            last = idx;
        }
        ok("随机取 3000 次没有连续重复", !repeat);

        System.out.println("== 动作 ==");
        for (PetAction a : PetAction.values()) {
            if (a == PetAction.IDLE) {
                eq("IDLE 时长", a.duration(), 0L);
            } else {
                ok(a + " 有正的时长", a.duration() > 0);
                ok(a + " 时长不超过 5 秒", a.duration() <= 5000L);
            }
        }
        PetAction[] pool = PetAction.idlePool();
        ok("待机动作池非空", pool.length > 0);
        for (PetAction a : pool) {
            ok("待机池里没有 IDLE", a != PetAction.IDLE);
            ok("待机池里的动作都能播", a.duration() > 0);
        }

        System.out.println(fail == 0 ? "\n全部通过" : "\n失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
