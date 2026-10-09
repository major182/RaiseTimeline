package com.raisetimeline.user;

import com.raisetimeline.follow.FollowRepository;
import com.raisetimeline.image.ImageStorage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 利用者から、利用者の要約（UserSummary。API 設計書 3.1）をまとめて作る。
 * 利用者ごとに SQL を発行せず、ID の配列でまとめて調べる（N+1 にしない。NF-PE-02）。
 */
@Service
public class UserSummaries {

    private final UserRepository users;
    private final FollowRepository follows;
    private final ImageStorage storage;

    public UserSummaries(UserRepository users, FollowRepository follows, ImageStorage storage) {
        this.users = users;
        this.follows = follows;
        this.storage = storage;
    }

    /** users と同じ順番で要約を返す。 */
    @Transactional(readOnly = true)
    public List<UserSummary> of(List<User> users, long meId) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> ids = users.stream().map(User::getId).toList();
        Set<Long> followed = new HashSet<>(follows.findFolloweeIdsAmong(meId, ids));
        return users.stream()
                .map(u -> new UserSummary(
                        u.getId(),
                        u.getUsername(),
                        u.getDisplayName(),
                        storage.urlOrNull(u.getAvatarKey()),
                        u.getBio(),
                        followed.contains(u.getId()),
                        u.getId() == meId))
                .toList();
    }

    public UserSummary of(User user, long meId) {
        return of(List.of(user), meId).getFirst();
    }

    /** 利用者の ID の一覧から、同じ順番で要約を返す。利用者は1回の SQL でまとめて読む。 */
    @Transactional(readOnly = true)
    public List<UserSummary> ofIds(List<Long> ids, long meId) {
        Map<Long, User> byId =
                users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return of(ids.stream().map(byId::get).toList(), meId);
    }
}
