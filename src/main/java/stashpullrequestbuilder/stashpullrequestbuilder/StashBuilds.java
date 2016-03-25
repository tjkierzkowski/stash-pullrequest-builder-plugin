package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.Run;
import hudson.model.Cause;
import hudson.model.Result;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
public class StashBuilds {
    private static final Logger logger = Logger.getLogger(StashBuilds.class.getName());
    private StashBuildTrigger trigger;
    private StashRepository repository;

    public StashBuilds(StashBuildTrigger trigger, StashRepository repository) {
        this.trigger = trigger;
        this.repository = repository;
    }

    public StashCause getCause(Run run) {
        Cause cause = run.getCause(StashCause.class);
        if (cause == null || !(cause instanceof StashCause)) {
            return null;
        }
        return (StashCause) cause;
    }

    public void onStarted(Run run) {
        StashCause cause = this.getCause(run);
        if (cause == null) {
            return;
        }
        try {
            run.setDescription(cause.getShortDescription());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Can't update build description", e);
        }
    }

    public void onCompleted(Run run) {
        StashCause cause = this.getCause(run);
        if (cause == null) {
            return;
        }
        Result result = run.getResult();
        String rootUrl = Jenkins.getInstance().getRootUrl();
        String buildUrl = "";
        if (rootUrl == null) {
            buildUrl = " PLEASE SET JENKINS ROOT URL FROM GLOBAL CONFIGURATION " + run.getUrl();
        }
        else {
            buildUrl = rootUrl + run.getUrl();
        }
        repository.deletePullRequestComment(cause.getPullRequestId(), cause.getBuildStartCommentId());

        StashPostBuildCommentAction comments = run.getAction(StashPostBuildCommentAction.class);
        String additionalComment = "";
        if(comments != null) {
            String buildComment = result == Result.SUCCESS ? comments.getBuildSuccessfulComment() : comments.getBuildFailedComment();

            if(buildComment != null && !buildComment.isEmpty()) {
              additionalComment = "\n\n" + buildComment;
            }
        }
        String duration = run.getDurationString();
        repository.postFinishedComment(cause.getPullRequestId(), cause.getSourceCommitHash(),
                cause.getDestinationCommitHash(), result, buildUrl,
                run.getNumber(), additionalComment, duration);
    }
}
