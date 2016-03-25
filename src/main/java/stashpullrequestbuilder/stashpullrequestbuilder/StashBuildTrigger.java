package stashpullrequestbuilder.stashpullrequestbuilder;

import antlr.ANTLRException;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Nathan McCarthy
 */
public class StashBuildTrigger extends Trigger  {
    private static final Logger logger = Logger.getLogger(StashBuildTrigger.class.getName());
    private final String projectPath;
    private final String cron;
    private final String stashHost;
    private final String credentialsId;
    private final String projectCode;
    private final String repositoryName;
    private final String ciSkipPhrases;
    private final String ciBuildPhrases;
    private final String targetBranchesToBuild;
    private final boolean ignoreSsl;
    private final boolean checkDestinationCommit;
    private final boolean checkMergeable;
    private final boolean checkNotConflicted;
    private final boolean onlyBuildOnComment;
    private final boolean deletePreviousBuildFinishComments;

    transient private StashPullRequestsBuilder stashPullRequestsBuilder;

    @Extension
    public static final StashBuildTriggerDescriptor descriptor = new StashBuildTriggerDescriptor();

    @DataBoundConstructor
    public StashBuildTrigger(
            String projectPath,
            String cron,
            String stashHost,
            String credentialsId,
            String projectCode,
            String repositoryName,
            String ciSkipPhrases,
            boolean ignoreSsl,
            boolean checkDestinationCommit,
            boolean checkMergeable,
            boolean checkNotConflicted,
            boolean onlyBuildOnComment,
            String ciBuildPhrases,
            boolean deletePreviousBuildFinishComments,
            String targetBranchesToBuild
            ) throws ANTLRException {
        super(cron);
        this.projectPath = projectPath;
        this.cron = cron;
        this.stashHost = stashHost;
        this.credentialsId = credentialsId;
        this.projectCode = projectCode;
        this.repositoryName = repositoryName;
        this.ciSkipPhrases = ciSkipPhrases;
        this.ciBuildPhrases = ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
        this.ignoreSsl = ignoreSsl;
        this.checkDestinationCommit = checkDestinationCommit;
        this.checkMergeable = checkMergeable;
        this.checkNotConflicted = checkNotConflicted;
        this.onlyBuildOnComment = onlyBuildOnComment;
        this.deletePreviousBuildFinishComments = deletePreviousBuildFinishComments;
        this.targetBranchesToBuild = targetBranchesToBuild;
    }

    public String getStashHost() {
        return stashHost;
    }

    public String getProjectPath() {
        return this.projectPath;
    }

    public String getCron() {
        return this.cron;
    }

    // Needed for Jelly Config
    public String getcredentialsId() {
    	return this.credentialsId;
    }

    private StandardUsernamePasswordCredentials getCredentials() {
        return CredentialsMatchers.firstOrNull(
                          CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, this.job, ACL.SYSTEM,
                                                                URIRequirementBuilder.fromUri(stashHost).build()),
                          CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
    }

    public String getUsername() {
        return this.getCredentials().getUsername();
    }

    public String getPassword() {
        return this.getCredentials().getPassword().getPlainText();
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getCiSkipPhrases() {
        return ciSkipPhrases;
    }

    public String getCiBuildPhrases() {
        return ciBuildPhrases == null ? "test this please" : ciBuildPhrases;
    }

    public boolean getCheckDestinationCommit() {
    	return checkDestinationCommit;
    }

    public boolean isIgnoreSsl() {
        return ignoreSsl;
    }

    public boolean getDeletePreviousBuildFinishComments() {
        return deletePreviousBuildFinishComments;
    }

    public String getTargetBranchesToBuild() {
        return targetBranchesToBuild;
    }

    //@Override
    public void start(Job<?, ?> job, boolean newInstance) {
        try {
            this.stashPullRequestsBuilder = StashPullRequestsBuilder.getBuilder();
            this.stashPullRequestsBuilder.setProject(job);
            this.stashPullRequestsBuilder.setTrigger(this);
            this.stashPullRequestsBuilder.setupBuilder();
        } catch(IllegalStateException e) {
            logger.log(Level.SEVERE, "Can't start trigger", e);
            return;
        }
        super.start(job, newInstance);
    }

    public static StashBuildTrigger getTrigger(AbstractProject job) {
        Trigger trigger = job.getTrigger(StashBuildTrigger.class);
        return (StashBuildTrigger) trigger;
        //return ParameterizedJobMixIn.getTrigger(job, StashBuildTrigger.class);
    }
    /**
    public static StashBuildTrigger getTrigger(Job job) {
        Trigger trigger = job.getTrigger(StashBuildTrigger.class);
        return (StashBuildTrigger)trigger;
    }
     */
    public StashPullRequestsBuilder getBuilder() {
        return this.stashPullRequestsBuilder;
    }

    public QueueTaskFuture<?> startJob(StashCause cause) {
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        values.put("sourceBranch", new StringParameterValue("sourceBranch", cause.getSourceBranch()));
        values.put("targetBranch", new StringParameterValue("targetBranch", cause.getTargetBranch()));
        values.put("sourceRepositoryOwner", new StringParameterValue("sourceRepositoryOwner", cause.getSourceRepositoryOwner()));
        values.put("sourceRepositoryName", new StringParameterValue("sourceRepositoryName", cause.getSourceRepositoryName()));
        values.put("pullRequestId", new StringParameterValue("pullRequestId", cause.getPullRequestId()));
        values.put("destinationRepositoryOwner", new StringParameterValue("destinationRepositoryOwner", cause.getDestinationRepositoryOwner()));
        values.put("destinationRepositoryName", new StringParameterValue("destinationRepositoryName", cause.getDestinationRepositoryName()));
        values.put("pullRequestTitle", new StringParameterValue("pullRequestTitle", cause.getPullRequestTitle()));
        values.put("sourceCommitHash", new StringParameterValue("sourceCommitHash", cause.getSourceCommitHash()));
        values.put("destinationCommitHash", new StringParameterValue("destinationCommitHash", cause.getDestinationCommitHash()));

        Map<String, String> additionalParameters = cause.getAdditionalParameters();
        if(additionalParameters != null){
        	for(String parameter : additionalParameters.keySet()){
        		values.put(parameter, new StringParameterValue(parameter, additionalParameters.get(parameter)));
        	}
        }

        //return this.job.scheduleBuild2(0, cause, new ParametersAction(new ArrayList(values.values())));
        AbstractProject temp = (AbstractProject) this.job;
        return temp.scheduleBuild2(0, cause, new ParametersAction(new ArrayList(values.values())));
    }
/**
    private Map<String, ParameterValue> getDefaultParameters() {
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        ParametersDefinitionProperty definitionProperty = this.job.getProperty(ParametersDefinitionProperty.class);

        if (definitionProperty != null) {
            for (ParameterDefinition definition : definitionProperty.getParameterDefinitions()) {
                values.put(definition.getName(), definition.getDefaultParameterValue());
            }
        }
        return values;
    }
*/
    @Override
    public void run() {
        if(this.getBuilder().getJob().isBuildable()) {
            logger.info("Build Skip.");
        } else {
            this.stashPullRequestsBuilder.run();
        }
        this.getDescriptor().save();
    }

    @Override
    public void stop() {
        super.stop();
    }

    public boolean isCheckMergeable() {
        return checkMergeable;
    }

    public boolean isCheckNotConflicted() {
        return checkNotConflicted;
    }

    public boolean isOnlyBuildOnComment() {
        return onlyBuildOnComment;
    }

    public static final class StashBuildTriggerDescriptor extends TriggerDescriptor {
        public StashBuildTriggerDescriptor() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Stash Pull Requests Builder";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            save();
            return super.configure(req, json);
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String source) {
            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardUsernameListBoxModel().withEmptySelection().withAll(
               CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM, URIRequirementBuilder.fromUri(source).build()));
        }
    }
}
