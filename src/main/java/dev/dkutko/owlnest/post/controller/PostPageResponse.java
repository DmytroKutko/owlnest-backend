package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.post.service.PostListPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "PostPageResponse",
        requiredProperties = {"items", "page", "links"}
)
public record PostPageResponse(
        List<PostResponse> items,
        PostPageMetadata page,
        PostPageLinks links
) {

    static PostPageResponse from(String requestCursor, PostListPage postPage) {
        String path = "/api/v1/posts";
        String self = pageLink(path, postPage.limit(), requestCursor);
        String next = postPage.hasMore()
                ? pageLink(path, postPage.limit(), postPage.nextCursor())
                : null;
        return new PostPageResponse(
                postPage.items().stream().map(PostResponse::from).toList(),
                new PostPageMetadata(postPage.limit(), postPage.hasMore(), postPage.nextCursor()),
                new PostPageLinks(self, next)
        );
    }

    private static String pageLink(String path, int limit, String cursor) {
        String link = path + "?limit=" + limit;
        return cursor == null ? link : link + "&cursor=" + cursor;
    }

    @Schema(
            name = "PostPageMetadata",
            requiredProperties = {"limit", "hasMore", "nextCursor"}
    )
    public record PostPageMetadata(
            int limit,
            boolean hasMore,
            @Schema(types = {"string", "null"}, maxLength = 1_024)
            String nextCursor
    ) {
    }

    @Schema(
            name = "PostPageLinks",
            requiredProperties = {"self", "next"}
    )
    public record PostPageLinks(
            @Schema(format = "uri-reference")
            String self,
            @Schema(types = {"string", "null"}, format = "uri-reference")
            String next
    ) {
    }
}
