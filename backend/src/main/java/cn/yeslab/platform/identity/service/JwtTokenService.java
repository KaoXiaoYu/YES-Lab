package cn.yeslab.platform.identity.service;

import cn.yeslab.platform.identity.model.AccountEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;

    public JwtTokenService(
            JwtEncoder encoder,
            @Value("${yeslab.security.jwt.issuer}") String issuer,
            @Value("${yeslab.security.jwt.ttl}") Duration ttl
    ) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public IssuedToken issue(AccountEntity account) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_" + account.getRole().name());
        account.getRole().permissions().forEach(permission -> authorities.add(permission.name()));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(account.getId().toString())
                .claim("username", account.getUsername())
                .claim("role", account.getRole().name())
                .claim("authorities", authorities)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
