package com.camerarental.config;

import com.camerarental.entity.Category;
import com.camerarental.entity.User;
import com.camerarental.entity.enums.Role;
import com.camerarental.repository.CategoryRepository;
import com.camerarental.repository.UserRepository;
import com.camerarental.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    private static final List<String[]> CATEGORIES = List.of(
            new String[]{"Máy ảnh DSLR", "Máy ảnh chuyên nghiệp"},
            new String[]{"Máy ảnh Mirrorless", "Nhẹ, hiện đại, không gương lật"},
            new String[]{"Máy ảnh Compact", "Nhỏ gọn, tiện lợi"},
            new String[]{"Máy ảnh Du Lịch", "Gọn nhẹ, dễ mang theo"},
            new String[]{"Máy ảnh Vlog", "Tự quay, Live stream"},
            new String[]{"Máy quay Video", "Chất lượng quay phim cao"},
            new String[]{"Máy ảnh Cinema", "Chuyên nghiệp, điện ảnh"},
            new String[]{"Action Camera", "Chống nước, gắn mũ, xe"},
            new String[]{"Camera 360", "Góc nhìn 360 độ"},
            new String[]{"Flycam", "Máy bay không người lái"},
            new String[]{"Lens", "Ống kính chuyên nghiệp"},
            new String[]{"Phụ kiện", "Tripod, gimbal, đèn,..."}
    );

    @Override
    public void run(String... args) {
        initializeAdmin();
        initializeCategories();
    }

    private void initializeAdmin() {
        if (!userRepository.existsByEmail("lekyanh1110@gmail.com")) {
            User admin = User.builder()
                    .fullName("Admin")
                    .email("lekyanh1110@gmail.com")
                    .password(passwordEncoder.encode("111111"))
                    .role(Role.ADMIN)
                    .isActive(true)
                    .build();
            userRepository.save(admin);
            log.info("Admin account created: lekyanh1110@gmail.com / 111111");
        }
    }

    private void initializeCategories() {
        for (String[] cat : CATEGORIES) {
            String name = cat[0];
            String description = cat[1];
            String slug = SlugUtil.toSlug(name);

            if (!categoryRepository.existsByName(name)) {
                Category category = Category.builder()
                        .name(name)
                        .slug(slug)
                        .description(description)
                        .isActive(true)
                        .build();
                categoryRepository.save(category);
                log.info("Category created: {}", name);
            }
        }
    }
}
