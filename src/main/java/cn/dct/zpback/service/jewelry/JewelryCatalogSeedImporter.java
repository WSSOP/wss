package cn.dct.zpback.service.jewelry;

import cn.dct.zpback.entity.PlanetaryJewelryProduct;
import cn.dct.zpback.entity.PlanetaryJewelrySku;
import cn.dct.zpback.mapper.PlanetaryJewelryProductMapper;
import cn.dct.zpback.mapper.PlanetaryJewelrySkuMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 行星珠宝建议｜内置示例商品导入器
 *
 * 用途：把 catalog.seed.json 导入商品表，让 V1 上线即有可展示数据，
 * 而不是让代码里的示例商品冒充真实商城数据（06 §12 真实性要求）。
 *
 * 关键语义：
 * - 导入的 source = 'SEED'，而商品表查询时，某个 planet::remedy 组合下
 *   一旦存在 source='ADMIN_UPLOAD' 的真实商品，该组合的 SEED 商品自动停止展示；
 *   因此运营上传真实商品后无需手动下架示例数据；
 * - 幂等：ON DUPLICATE KEY UPDATE，重复启动不会插出重复行；
 * - 已存在的真实商品不会被覆盖：只导入 SEED，且不会改动 ADMIN_UPLOAD 行。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class JewelryCatalogSeedImporter implements ApplicationRunner {

    private final PlanetaryJewelryProductMapper productMapper;
    private final PlanetaryJewelrySkuMapper skuMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        try (InputStream in = new ClassPathResource("planetary-jewelry/catalog.seed.json").getInputStream()) {
            JsonNode root = mapper.readTree(in);
            List<PlanetaryJewelryProduct> products = new ArrayList<>();
            List<PlanetaryJewelrySku> skus = new ArrayList<>();

            for (JsonNode p : root.path("products")) {
                products.add(toProduct(p));
                for (JsonNode s : p.path("skus")) {
                    skus.add(toSku(s, String.valueOf(p.path("product_id").asText())));
                }
            }

            // 必须先写 product 再写 sku：sku.product_id 上有外键约束，顺序反了会失败
            for (PlanetaryJewelryProduct p : products) {
                productMapper.upsert(p);
            }
            for (PlanetaryJewelrySku s : skus) {
                skuMapper.upsert(s);
            }
            log.info("行星珠宝示例商品导入完成：products={} skus={}（source=SEED，运营上传真实商品后同组合示例自动下架）",
                    products.size(), skus.size());
        } catch (Exception e) {
            // 导入失败不能阻塞应用启动：真实商品为空时应返回 NO_MAPPED_PRODUCT，
            // 而不是让整个功能 500（05 §7「没有上传对应商品」是正常状态）
            log.error("行星珠宝示例商品导入失败，商城将按空目录处理", e);
        }
    }

    private PlanetaryJewelryProduct toProduct(JsonNode p) {
        PlanetaryJewelryProduct product = new PlanetaryJewelryProduct();
        product.setProductId(p.path("product_id").asText());
        product.setTitle(p.path("title").asText());
        product.setPlanetCode(p.path("planet_code").asText().toUpperCase());
        product.setRemedyType(p.path("remedy_type").asText().toUpperCase());
        product.setDescription(p.path("description").asText(null));
        product.setImages(p.path("images").toString());
        product.setAutoRecommendable(p.path("auto_recommendable").asBoolean(true));
        product.setPublishStatus(p.path("publish_status").asText("DRAFT"));
        product.setAdminRecommendationPriority(p.path("admin_recommendation_priority").asInt(0));
        product.setGiftAvailable(p.path("gift_available").asBoolean(true));
        // seed 商品没有 high_threshold 标记，统一 false
        product.setHighThresholdGemstone(false);
        // 与 saveSku 同理：MyBatis 会把 null 当显式值传下去，这里补齐非空列默认值
        product.setAutoRecommendable(p.path("auto_recommendable").asBoolean(true));
        product.setGiftAvailable(p.path("gift_available").asBoolean(true));
        product.setAdminRecommendationPriority(p.path("admin_recommendation_priority").asInt(0));
        product.setSource("SEED");
        return product;
    }

    private PlanetaryJewelrySku toSku(JsonNode s, String productId) {
        PlanetaryJewelrySku sku = new PlanetaryJewelrySku();
        sku.setSkuId(s.path("sku_id").asText());
        sku.setProductId(productId);
        sku.setVariantAttributes(s.path("variant_attributes").toString());
        sku.setPrice(BigDecimal.valueOf(s.path("price").asDouble(0)));
        sku.setInventoryQuantity(s.path("inventory_quantity").asInt(0));
        // status / sale_enabled / restock_waitlist_enabled 种子没有显式给，
        // 但 MyBatis 会把 null 当作显式值传下去（覆盖列默认值），所以这里必须显式赋值
        sku.setStatus("ACTIVE");
        sku.setSaleEnabled(true);
        sku.setRestockWaitlistEnabled(true);
        sku.setSource("SEED");
        return sku;
    }
}