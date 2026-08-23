package fun.commons.benefit4j.service;

public interface BenefitAuthService {
    Object postToken(String grantType, String clientId, String clientSecret);
}
