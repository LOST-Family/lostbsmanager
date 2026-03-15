package commands.admin;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Club;
import datawrapper.KickpointReason;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class copyreasons extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("copyreasons"))
            return;
        event.deferReply().queue();
        String title = "Kickpunkt-Gründe kopieren";

        OptionMapping clubOption = event.getOption("club");

        if (clubOption == null) {
            event.getHook().editOriginalEmbeds(
                    MessageUtil.buildEmbed(title, "Der Club-Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        String sourceClubtag = clubOption.getAsString();
        Club sourceClub = new Club(sourceClubtag);

        if (!sourceClub.ExistsDB()) {
            event.getHook()
                    .editOriginalEmbeds(
                            MessageUtil.buildEmbed(title, "Dieser Club existiert nicht.", MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        if (sourceClubtag.equals("warteliste")) {
            event.getHook().editOriginalEmbeds(
                    MessageUtil.buildEmbed(title, "Du kannst nicht von der Warteliste kopieren.",
                            MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        User userexecuted = new User(event.getUser().getId());
        if (!userexecuted.isAdmin()) {
            event.getHook()
                    .editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Du musst Admin sein, um diesen Befehl ausführen zu können.", MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        // Get all kickpoint reasons from source club
        ArrayList<KickpointReason> sourceReasons = sourceClub.getKickpointReasons();

        if (sourceReasons.isEmpty()) {
            event.getHook().editOriginalEmbeds(
                    MessageUtil.buildEmbed(title, "Der Quell-Club hat keine Kickpunkt-Gründe.",
                            MessageUtil.EmbedType.ERROR))
                    .queue();
            return;
        }

        // Get all clubs and copy reasons to each (except warteliste and source club)
        ArrayList<String> allClubs = DBManager.getAllClubs();
        int clubsUpdated = 0;

        for (String targetClubtag : allClubs) {
            // Skip warteliste and source club
            if (targetClubtag.equals("warteliste") || targetClubtag.equals(sourceClubtag)) {
                continue;
            }

            // Delete all existing kickpoint reasons for target club
            DBUtil.executeUpdate("DELETE FROM kickpoint_reasons WHERE club_tag = ?", targetClubtag);

            // Copy all reasons from source club
            for (KickpointReason reason : sourceReasons) {
                DBUtil.executeUpdate(
                        "INSERT INTO kickpoint_reasons (name, club_tag, amount, index) VALUES (?, ?, ?, ?)",
                        reason.getReason(), targetClubtag, reason.getAmount(), reason.getIndex());
            }

            clubsUpdated++;
        }

        String desc = "Die Kickpunkt-Gründe wurden erfolgreich kopiert.\n\n";
        desc += "**Quell-Club:** " + sourceClub.getInfoStringDB() + "\n";
        desc += "**Anzahl Gründe:** " + sourceReasons.size() + "\n";
        desc += "**Aktualisierte Clubs:** " + clubsUpdated;

        event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
    }

    @SuppressWarnings("null")
    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("copyreasons"))
            return;

        String focused = event.getFocusedOption().getName();
        String input = event.getFocusedOption().getValue();

        if (focused.equals("club")) {
            List<Command.Choice> choices = DBManager.getClubsAutocompleteNoWaitlist(input);
            event.replyChoices(choices).queue();
        }
    }
}



