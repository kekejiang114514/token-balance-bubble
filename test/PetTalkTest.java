import com.coco.balancebubble.PetTalk;

import java.util.HashSet;
import java.util.Set;

/** JVM 单测：桌宠卖萌语料库。 */
public class PetTalkTest {
    static int fail = 0;

    static void ok(String what, boolean cond) {
        if (!cond) {
            fail++;
            System.out.println("  FAIL " + what);
        }
    }

    public static void main(String[] args) {
        System.out.println("== 语料库 ==");
        System.out.println("  句子数：" + PetTalk.PHRASES.length);
        ok("句子数足够（>=20）", PetTalk.PHRASES.length >= 20);

        Set<String> set = new HashSet<String>();
        for (String p : PetTalk.PHRASES) {
            ok("句子非空", p != null && p.trim().length() > 0);
            ok("句子不超长（<=12 字，否则气泡会撑破）：" + p, p.length() <= 12);
            ok("句子不重复：" + p, set.add(p));
        }

        System.out.println("== 随机取词 ==");
        // 连续两次不应重复（random() 的实现约定）
        String prev = null;
        int dup = 0;
        int outside = 0;
        for (int i = 0; i < 5000; i++) {
            String s = PetTalk.random();
            if (!set.contains(s)) outside++;
            if (s.equals(prev)) dup++;
            prev = s;
        }
        ok("5000 次取词都在语料库内", outside == 0);
        ok("5000 次取词没有连续重复", dup == 0);

        // 覆盖度：多取几次应该能覆盖全部句子
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < 2000; i++) seen.add(PetTalk.random());
        ok("2000 次取词覆盖全部 " + PetTalk.PHRASES.length + " 句（实际 " + seen.size() + "）",
                seen.size() == PetTalk.PHRASES.length);

        System.out.println(fail == 0 ? "PetTalkTest OK" : "PetTalkTest 失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
