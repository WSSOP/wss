package cn.dct.zpback.engine.synbasic;

import cn.dct.zpback.constant.SynBasicConstants;
import cn.dct.zpback.dto.synbasic.SynBasicDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 156 Canonical Library 规则资产
 *
 * 生产只读取统一 156 库（不再并行读四个旧包）；
 * 同时提供：
 * - stable ID 归一化（Dictionary）；
 * - (planet_domain, source_id) 作用域查询（Lookup Index）；
 * - slot 白名单 / 允许 pattern / scene pool / Evidence Hint 静态映射。
 */
@Component
@Slf4j
public class SynBasicAssets {

    @Autowired
    private ObjectMapper objectMapper;

    private SynBasicDtos.CanonicalLibrary library;
    private SynBasicDtos.NamingDictionary dictionary;
    private SynBasicDtos.LookupIndex lookupIndex;

    /** canonical_entry_id → entry */
    private Map<String, SynBasicDtos.CanonicalEntry> entriesById = Collections.emptyMap();
    /** planet_domain → (source_id → entry) */
    private Map<String, Map<String, SynBasicDtos.CanonicalEntry>> entriesByPlanetSource = Collections.emptyMap();
    /** 归一化别名 → SIGN_xxx */
    private Map<String, String> signByAlias = Collections.emptyMap();
    /** 归一化别名 → NAK_xx_xxx */
    private Map<String, String> nakByAlias = Collections.emptyMap();

    @PostConstruct
    public void load() {
        try {
            library = read(SynBasicConstants.RES_CANONICAL_LIBRARY, SynBasicDtos.CanonicalLibrary.class);
            dictionary = read(SynBasicConstants.RES_NAMING_DICTIONARY, SynBasicDtos.NamingDictionary.class);
            lookupIndex = read(SynBasicConstants.RES_LOOKUP_INDEX, SynBasicDtos.LookupIndex.class);

            if (library == null || library.getEntries() == null
                    || library.getEntries().size() != 156) {
                throw new IllegalStateException("156 canonical library 条目数异常");
            }
            if (dictionary == null || dictionary.getSigns() == null || dictionary.getSigns().size() != 12
                    || dictionary.getNakshatras() == null || dictionary.getNakshatras().size() != 27) {
                throw new IllegalStateException("naming dictionary 条目数异常");
            }
            if (lookupIndex == null || lookupIndex.getSlots_by_planet() == null) {
                throw new IllegalStateException("lookup index 缺失");
            }

            Map<String, SynBasicDtos.CanonicalEntry> byId = new LinkedHashMap<>();
            Map<String, Map<String, SynBasicDtos.CanonicalEntry>> byPlanet = new LinkedHashMap<>();
            for (SynBasicDtos.CanonicalEntry e : library.getEntries()) {
                byId.put(e.getCanonical_entry_id(), e);
                byPlanet.computeIfAbsent(e.getPlanet_domain(), k -> new LinkedHashMap<>())
                        .put(e.getSource_id(), e);
            }
            entriesById = Collections.unmodifiableMap(byId);
            entriesByPlanetSource = Collections.unmodifiableMap(byPlanet);

            Map<String, String> signs = new LinkedHashMap<>();
            for (SynBasicDtos.SignEntry s : dictionary.getSigns()) {
                signs.put(normalize(s.getId()), s.getId());
                signs.put(normalize(s.getCanonical_en()), s.getId());
                signs.put(normalize(s.getDisplay_zh()), s.getId());
                if (s.getAliases() != null) {
                    for (String a : s.getAliases()) {
                        signs.putIfAbsent(normalize(a), s.getId());
                    }
                }
            }
            signByAlias = Collections.unmodifiableMap(signs);

            Map<String, String> naks = new LinkedHashMap<>();
            for (SynBasicDtos.NakEntry n : dictionary.getNakshatras()) {
                naks.put(normalize(n.getId()), n.getId());
                naks.put(normalize(n.getCanonical_en()), n.getId());
                naks.put(normalize(n.getDisplay_zh()), n.getId());
                if (n.getAliases() != null) {
                    for (String a : n.getAliases()) {
                        naks.putIfAbsent(normalize(a), n.getId());
                    }
                }
            }
            nakByAlias = Collections.unmodifiableMap(naks);

            log.info("双人合盘基础互动资产加载完成: entries={}, library={}, schema={}, resolver={}, render={}",
                    entriesById.size(), library.getContent_library_version(),
                    SynBasicConstants.SCHEMA_VERSION, SynBasicConstants.RESOLVER_VERSION,
                    SynBasicConstants.RENDER_PROFILE_VERSION);
        } catch (Exception e) {
            throw new IllegalStateException("双人合盘基础互动规则资产加载失败: " + e.getMessage(), e);
        }
    }

    private <T> T read(String path, Class<T> type) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }

    /** 归一化：大小写不敏感、去首尾空白、折叠内部空白、空格/连字符/下划线视为分隔符 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase().replaceAll("[\\s\\-_]+", "");
    }

    /**
     * stable ID 归一化（Dictionary 为唯一权威）
     * 优先 stable ID；其次 canonical english；再 explicit alias；最后中文显示名。
     * 不做 fuzzy nearest-match。
     *
     * @return canonical stable ID，无法解析返回 null
     */
    public String normalizeSignId(String raw) {
        return signByAlias.get(normalize(raw));
    }

    public String normalizeNakId(String raw) {
        return nakByAlias.get(normalize(raw));
    }

    /** 显示名必须由 Dictionary rebound，不信任上游旧 label */
    public String signLabelZh(String signId) {
        for (SynBasicDtos.SignEntry s : dictionary.getSigns()) {
            if (s.getId().equals(signId)) {
                return s.getDisplay_zh();
            }
        }
        return null;
    }

    public String nakLabelZh(String nakId) {
        for (SynBasicDtos.NakEntry n : dictionary.getNakshatras()) {
            if (n.getId().equals(nakId)) {
                return n.getDisplay_zh();
            }
        }
        return null;
    }

    /** 作用域查询：(planet_domain, source_id) → entry；不得只用 source_id 查 */
    public SynBasicDtos.CanonicalEntry lookup(String planetDomain, String sourceId) {
        Map<String, SynBasicDtos.CanonicalEntry> byPlanet = entriesByPlanetSource.get(planetDomain);
        return byPlanet == null ? null : byPlanet.get(sourceId);
    }

    public List<String> slotsOf(String planetDomain) {
        return lookupIndex.getSlots_by_planet().getOrDefault(planetDomain, Collections.emptyList());
    }

    public List<String> allowedPatternsOf(String planetDomain) {
        return lookupIndex.getAllowed_patterns_by_planet().getOrDefault(planetDomain, Collections.emptyList());
    }

    public List<String> scenePoolOf(String planetDomain) {
        return lookupIndex.getScene_pool_by_planet().getOrDefault(planetDomain, Collections.emptyList());
    }

    public String contentLibraryVersion() {
        return library == null ? SynBasicConstants.CONTENT_LIBRARY_VERSION : library.getContent_library_version();
    }

    /** Evidence Hint 静态映射（A1.4：planet + source_mode → 固定文案） */
    public Map<String, Map<String, String>> evidenceHints() {
        return lookupIndex == null || lookupIndex.getEvidence_hint_by_planet_and_source_mode() == null
                ? Collections.emptyMap()
                : lookupIndex.getEvidence_hint_by_planet_and_source_mode();
    }

    public SynBasicDtos.CanonicalLibrary getLibrary() {
        return library;
    }
}
