/*
 * Minecraft Forge
 * Copyright (c) 2016-2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.server;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.LoadingFailedException;
import net.minecraftforge.fml.LogicalSidedProvider;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraftforge.fml.loading.LogMarkers.LOADING;

public class ServerModLoader
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean hasErrors = false;

    public static void load() {
        LogicalSidedProvider.setServer(()-> {
            throw new IllegalStateException("Unable to access server yet");
        });
        LanguageHook.loadForgeAndMCLangs();
        try {
            ModLoader.get().gatherAndInitializeMods(ModWorkManager.syncExecutor(), ModWorkManager.parallelExecutor(), ()->{});
            ModLoader.get().loadMods(ModWorkManager.syncExecutor(), ModWorkManager.parallelExecutor(), e-> CompletableFuture.runAsync(()->{}, e), e->CompletableFuture.runAsync(()->{}, e), ()->{});
            ModLoader.get().finishMods(ModWorkManager.syncExecutor(), ModWorkManager.parallelExecutor(), ()->{});
        } catch (LoadingFailedException error) {
            ServerModLoader.hasErrors = true;
            final CrashReport crashReport = CrashReport.makeCrashReport(error, "Mod loading error has occurred");
            error.getErrors().forEach(mle -> {
                final CrashReportCategory category = crashReport.makeCategory(mle.getModInfo().getModId());
                category.applyStackTrace(mle.getCause());
                category.addDetail("Failure message", mle.getCleanMessage());
                category.addDetail("Exception message", mle.getCause().toString());
                category.addDetail("Mod Version", mle.getModInfo().getVersion().toString());
                category.addDetail("Mod Issue URL", ((ModFileInfo)mle.getModInfo().getOwningFile()).getConfigElement("issueTrackerURL").orElse("NOT PROVIDED"));
            });
            final File file1 = new File("crash-reports");
            final File file2 = new File(file1, "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + "-server.txt");
            if (crashReport.saveToFile(file2)) {
                LOGGER.fatal("Crash report saved to {}", file2);
            } else {
                LOGGER.fatal("Failed to save crash report");
            }
            System.out.print(crashReport.getCompleteReport());
            throw error;
        }
        List<ModLoadingWarning> warnings = ModLoader.get().getWarnings();
        if (!warnings.isEmpty()) {
            LOGGER.warn(LOADING, "Mods loaded with {} warnings", warnings.size());
            warnings.forEach(warning -> LOGGER.warn(LOADING, warning.formatToString()));
        }
        MinecraftForge.EVENT_BUS.start();
    }

    public static boolean hasErrors() {
        return ServerModLoader.hasErrors;
    }
}
