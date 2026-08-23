package com.groundwork.evidence.application.port.out;

import com.groundwork.evidence.domain.ConnectorConnection;
import com.groundwork.evidence.domain.PullRequestSnapshot;

import java.util.Optional;

public interface SourceControlPort {
    Optional<PullRequestSnapshot> fetchPullRequest(
        ConnectorConnection connection, String repositoryFullName, int pullRequestNumber,
        String baseSha, String headSha);

    void publishCheck(ConnectorConnection connection, String repositoryFullName, String headSha,
                      CheckPublication publication);

    boolean isAvailable();

    record CheckPublication(String externalId, String name, String status, String conclusion,
                            String title, String summary, String detailsUrl) {}
}
