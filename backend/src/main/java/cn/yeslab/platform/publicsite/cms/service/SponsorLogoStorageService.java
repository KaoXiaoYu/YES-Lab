package cn.yeslab.platform.publicsite.cms.service;

import cn.yeslab.platform.member.service.MemberAvatarStorageService;
import cn.yeslab.platform.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@Service
public class SponsorLogoStorageService {
    private final MemberAvatarStorageService images;

    public SponsorLogoStorageService(@Value("${yeslab.storage.sponsors-directory:./data/sponsors}") String directory) {
        images = new MemberAvatarStorageService(directory);
    }

    public String upload(MultipartFile logo) {
        UUID id = UUID.randomUUID();
        try { images.store(id, logo); }
        catch (ApiException error) { throw new ApiException(error.getStatus(), error.getMessage().replace("头像", "赞助商 Logo")); }
        return "/api/v1/public/sponsors/logos/" + id;
    }

    public MemberAvatarStorageService.StoredAvatarResource resource(UUID id) {
        return images.resource(id);
    }
}
