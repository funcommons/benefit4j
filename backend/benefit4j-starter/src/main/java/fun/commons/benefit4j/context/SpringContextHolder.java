package fun.commons.benefit4j.context;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 容器静态访问器。
 * <p>
 * 用于非 Spring 管理的对象 (如 MyBatis TypeHandler 反射实例化) 取 Spring Bean。
 * aware 回调在 Bean 初始化阶段, 早于 Mapper 解析 (Mapper Bean 实例化时才解析注解),
 * 故静态引用在 TypeHandler 无参构造调用时已就绪。
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static <T> T getBean(String name, Class<T> type) {
        if (context == null) {
            throw new IllegalStateException("Spring 容器未就绪 (SpringContextHolder.context == null)");
        }
        return context.getBean(name, type);
    }
}
