package kr.syeyoung.dungeonsguide.features.impl.party.playerpreview;

import com.google.common.base.Supplier;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import io.github.moulberry.hychat.HyChat;
import io.github.moulberry.hychat.chat.ChatManager;
import io.github.moulberry.hychat.gui.GuiChatBox;
import kr.syeyoung.dungeonsguide.config.guiconfig.ConfigPanelCreator;
import kr.syeyoung.dungeonsguide.config.guiconfig.FeatureEditPane;
import kr.syeyoung.dungeonsguide.config.guiconfig.GuiConfig;
import kr.syeyoung.dungeonsguide.config.guiconfig.PanelDefaultParameterConfig;
import kr.syeyoung.dungeonsguide.features.FeatureParameter;
import kr.syeyoung.dungeonsguide.features.FeatureRegistry;
import kr.syeyoung.dungeonsguide.features.SimpleFeature;
import kr.syeyoung.dungeonsguide.features.impl.party.api.*;
import kr.syeyoung.dungeonsguide.features.listener.ChatListener;
import kr.syeyoung.dungeonsguide.features.listener.GuiClickListener;
import kr.syeyoung.dungeonsguide.features.listener.GuiPostRenderListener;
import kr.syeyoung.dungeonsguide.features.text.PanelTextParameterConfig;
import kr.syeyoung.dungeonsguide.features.text.TextHUDFeature;
import kr.syeyoung.dungeonsguide.gui.MPanel;
import kr.syeyoung.dungeonsguide.party.PartyManager;
import kr.syeyoung.dungeonsguide.utils.RenderUtils;
import kr.syeyoung.dungeonsguide.utils.TextUtils;
import kr.syeyoung.dungeonsguide.utils.XPUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FeatureViewPlayerOnJoin extends SimpleFeature implements GuiPostRenderListener, ChatListener, GuiClickListener {

    public FeatureViewPlayerOnJoin() {
        super("Party Kicker", "View player stats when join", "view player rendering when joining/someone joins the party", "partykicker.viewstats", true);
        this.parameters.put("datarenderers", new FeatureParameter<List<String>>("datarenderers", "DataRenderers","Datarenderssdasd", new ArrayList<>(Arrays.asList(
                "catalv", "selected_class_lv", "dungeon_catacombs_higheststat", "dungeon_master_catacombs_higheststat", "skill_combat_lv", "skill_foraging_lv", "skill_mining_lv", "fairysouls", "dummy"
        )), "stringlist"));
    }

    private Rectangle popupRect;
    private String lastuid; // actually current uid
    private Future<Optional<PlayerProfile>> profileFuture;
    private Future<Optional<GameProfile>> gfFuture;
    private Future<SkinFetchur.SkinSet> skinFuture;
    private FakePlayer fakePlayer;
    private boolean drawInv = false;
    @SneakyThrows
    @Override
    public void onGuiPostRender(GuiScreenEvent.DrawScreenEvent.Post rendered) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiChat)) {
            cancelRender();
            return;
        }
        ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
        IChatComponent ichatcomponent = getHoveredComponent(scaledResolution);
        String uid = null;
        if (ichatcomponent != null && ichatcomponent.getChatStyle().getChatHoverEvent() instanceof HoverEventRenderPlayer) {
            uid = ((HoverEventRenderPlayer) ichatcomponent.getChatStyle().getChatHoverEvent()).getUuid();
        }
        reqRender(uid);
    }

    public void cancelRender() {
        popupRect = null;
        profileFuture = null;
        lastuid = null;
        gfFuture = null;
        skinFuture=  null;
        fakePlayer= null;
        drawInv = false;
    }

    public void reqRender(String uid) {
        ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
        int width = scaledResolution.getScaledWidth();
        int height = scaledResolution.getScaledHeight();
        int mouseX = Mouse.getX() * width / Minecraft.getMinecraft().displayWidth;
        int mouseY = height - Mouse.getY() * height / Minecraft.getMinecraft().displayHeight - 1;


        if (!((popupRect != null && (popupRect.contains(mouseX, mouseY) || drawInv)) || uid != null && uid.equals(lastuid))) {
            cancelRender();
        }

        if (uid != null && !uid.equals(lastuid) && (popupRect==null || (!popupRect.contains(mouseX, mouseY) && !drawInv)) ) {
            cancelRender();
            lastuid = uid;
        }
        if (lastuid == null) return;


        if (popupRect == null) {
            popupRect = new Rectangle(mouseX, mouseY, 220, 220);
            if (popupRect.y + popupRect.height > scaledResolution.getScaledHeight()) {
                popupRect.y -= popupRect.y + popupRect.height - scaledResolution.getScaledHeight();
            }
        }

        if (profileFuture == null) {
            profileFuture = ApiFetchur.fetchMostRecentProfileAsync(lastuid, FeatureRegistry.PARTYKICKER_APIKEY.getAPIKey());
        }

        if (gfFuture == null) {
            gfFuture = ApiFetchur.getSkinGameProfileByUUIDAsync(lastuid);
        }
        boolean plsSetAPIKEY = false;
        if (skinFuture == null && gfFuture.isDone()) {
            try {
                skinFuture = SkinFetchur.getSkinSet(gfFuture.get().orElse(null));
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        try {
            if (fakePlayer == null && skinFuture != null && profileFuture != null && skinFuture.isDone() && profileFuture.isDone() && profileFuture.get().isPresent()) {
                fakePlayer = new FakePlayer(gfFuture.get().orElse(null), skinFuture.get(), profileFuture.get().orElse(null));
            }
        } catch (InterruptedException | ExecutionException e) {
            plsSetAPIKEY = true;
        }


        try {
            render(popupRect, scaledResolution, mouseX, mouseY, plsSetAPIKEY ? null : (profileFuture.isDone() ? profileFuture.get() : null), plsSetAPIKEY);
        } catch (InterruptedException | ExecutionException e) {
        }

    }

    private void render(Rectangle popupRect, ScaledResolution scaledResolution, int mouseX, int mouseY, Optional<PlayerProfile> playerProfile, boolean apiKeyPlsSet) {

        GlStateManager.pushMatrix();
        GlStateManager.translate(popupRect.x, popupRect.y, 0);
        Gui.drawRect(0,0, popupRect.width, popupRect.height, 0xFF23272a);
        Gui.drawRect(2,2, popupRect.width-2, popupRect.height-2, 0XFF2c2f33);
        if (apiKeyPlsSet) {
            Minecraft.getMinecraft().fontRendererObj.drawString("Please set API KEY on /dg -> Party Kicker", 5,5, 0xFFFFFFFF);
            GlStateManager.popMatrix();
            return;
        }
        if (playerProfile == null) {
            Minecraft.getMinecraft().fontRendererObj.drawString("Fetching data...", 5,5, 0xFFFFFFFF);
            GlStateManager.popMatrix();
            return;
        }
        if (!playerProfile.isPresent()) {
            Minecraft.getMinecraft().fontRendererObj.drawString("User could not be found", 5,5, 0xFFFFFFFF);
            GlStateManager.popMatrix();
            return;
        }
        int relX = mouseX - popupRect.x;
        int relY = mouseY - popupRect.y;
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;


        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        MPanel.clip(scaledResolution, popupRect.x, popupRect.y, popupRect.width, popupRect.height);

        Gui.drawRect(0,168, 90, 195, 0xFF23272a);
        Gui.drawRect(2,170, 88, 193, new Rectangle(2,170,86,23).contains(relX, relY) ? 0xFFff7777 : 0xFFFF3333);
        fr.drawString("Kick", (90 - fr.getStringWidth("Kick")) / 2,(364 - fr.FONT_HEIGHT) / 2, 0xFFFFFFFF);
        Gui.drawRect(0,193, 90, 220, 0xFF23272a);
        Gui.drawRect(2,195, 88, 218, new Rectangle(2,195,86,23).contains(relX, relY) ? 0xFF859DF0 : 0xFF7289da);
        fr.drawString("Invite", (90 - fr.getStringWidth("Invite")) / 2,(414 - fr.FONT_HEIGHT) / 2, 0xFFFFFFFF);

        GlStateManager.pushMatrix();

        GlStateManager.translate(95, 5, 0);
        int culmutativeY = 5;
        DataRenderer dataRendererToHover = null;
        for (String datarenderers : this.<List<String>>getParameter("datarenderers").getValue()) {
            DataRenderer dataRenderer = DataRendererRegistry.getDataRenderer(datarenderers);
            Dimension dim;
            if (dataRenderer == null) {
                fr.drawString("Couldn't find Datarenderer", 0,0, 0xFFFF0000);
                fr.drawString(datarenderers, 0,fr.FONT_HEIGHT, 0xFFFF0000);
                dim = new Dimension(0, fr.FONT_HEIGHT * 2);
            } else {
                GlStateManager.pushMatrix();
                dim = dataRenderer.renderData(playerProfile.get());
                GlStateManager.popMatrix();
            }
            if (relX >= 95 && relX <= popupRect.width && relY >= culmutativeY && relY < culmutativeY+dim.height && dataRenderer != null) {
                dataRendererToHover = dataRenderer;
            }
            culmutativeY += dim.height;
            GlStateManager.translate(0,dim.height,0);
        }

        GlStateManager.popMatrix();

        Gui.drawRect(0,0, 90, 170, 0xFF23272a);
        Gui.drawRect(2,2, 88, 168, 0xFF444444);
        Gui.drawRect(80,159, 90, 170, 0xFF23272a);
        Gui.drawRect(82,161, 88, 168, 0xFF444444);
        fr.drawString("§eI", 83,161,-1);
        GlStateManager.color(1, 1, 1, 1.0F);
        if (fakePlayer != null) {
            GuiInventory.drawEntityOnScreen(45, 150, 60, -(mouseX - popupRect.x - 75), 0, fakePlayer);
            fr.drawString(fakePlayer.getName(), (90 - fr.getStringWidth(fakePlayer.getName())) / 2, 15, 0xFFEFFF00);

            ItemStack toHover = null;
            if (relX > 20 && relX < 70) {
                if (33<=relY && relY <= 66) {
                    toHover = fakePlayer.getInventory()[3];
                } else if (66 <= relY && relY <= 108) {
                    toHover = fakePlayer.getInventory()[2];
                } else if (108 <= relY && relY <= 130) {
                    toHover = fakePlayer.getInventory()[1];
                } else if (130 <= relY && relY <= 154) {
                    toHover = fakePlayer.getInventory()[0];
                }
            } else if (relX > 0 && relX <= 20) {
                if (80 <= relY && relY <= 120) {
                    toHover = fakePlayer.inventory.mainInventory[fakePlayer.inventory.currentItem];
                }
            }

            if (toHover != null) {
                List<String> list = toHover.getTooltip(Minecraft.getMinecraft().thePlayer, Minecraft.getMinecraft().gameSettings.advancedItemTooltips);
                for (int i = 0; i < list.size(); ++i) {
                    if (i == 0) {
                        list.set(i, toHover.getRarity().rarityColor + (String)list.get(i));
                    } else {
                        list.set(i, EnumChatFormatting.GRAY + (String)list.get(i));
                    }
                }
                FontRenderer font = toHover.getItem().getFontRenderer(toHover);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                FontRenderer theRenderer = (font == null ? fr : font);
                int minY = scaledResolution.getScaledHeight() - (list.size()+4) * theRenderer.FONT_HEIGHT - popupRect.y;
                RenderUtils.drawHoveringText(list,relX, Math.min(minY, relY), theRenderer);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        } else {
            fr.drawString("Loading", 5,35, 0xFFEFFF00);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (dataRendererToHover != null && !drawInv) {
            dataRendererToHover.onHover(playerProfile.get(), relX, relY);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        if (drawInv) {
            int startX = 81;
            int startY = 86;
            MPanel.clip(scaledResolution, popupRect.x+startX-1, popupRect.y+startY-1, 164, 74);
            GlStateManager.translate(startX,startY,1);
            Gui.drawRect(-1,-1,163,73, 0xFF000000);
            GlStateManager.disableLighting();
            ItemStack toHover = null;
            int rx = relX - startX;
            int ry = relY - startY;


            GlStateManager.pushAttrib();
            GlStateManager.disableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.disableLighting();
            for (int i = 0; i < playerProfile.get().getInventory().length; i++) {
                int x = (i%9) * 18;
                int y = (i/9) * 18;
                if (x <= rx && rx<x+18 && y<=ry&&ry<y+18) {
                    toHover = playerProfile.get().getInventory()[(i+9) % 36];
                }
                Gui.drawRect(x,y,x+18,y+18, 0xFF000000);
                Gui.drawRect(x+1,y+1,x+17,y+17, 0xFF666666);
                GlStateManager.color(1, 1, 1, 1.0F);

                Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(playerProfile.get().getInventory()[(i+9) % 36], (i%9) * 18+1,(i/9) * 18+1);
            }
            GlStateManager.popAttrib();
            if (toHover != null) {
                List<String> list = toHover.getTooltip(Minecraft.getMinecraft().thePlayer, Minecraft.getMinecraft().gameSettings.advancedItemTooltips);
                for (int i = 0; i < list.size(); ++i) {
                    if (i == 0) {
                        list.set(i, toHover.getRarity().rarityColor + (String)list.get(i));
                    } else {
                        list.set(i, EnumChatFormatting.GRAY + (String)list.get(i));
                    }
                }
                FontRenderer font = toHover.getItem().getFontRenderer(toHover);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                FontRenderer theRenderer = (font == null ? fr : font);
                int minY = scaledResolution.getScaledHeight() - (list.size()+4) * theRenderer.FONT_HEIGHT - popupRect.y;
                RenderUtils.drawHoveringText(list,rx, Math.min(minY-startY, ry), theRenderer);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);


        GlStateManager.popMatrix(); // 33 66 108 130 154 // 5 75
    }
    @Override
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre mouseInputEvent) {
        ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
        int width = scaledResolution.getScaledWidth();
        int height = scaledResolution.getScaledHeight();
        int mouseX = Mouse.getX() * width / Minecraft.getMinecraft().displayWidth;
        int mouseY = height - Mouse.getY() * height / Minecraft.getMinecraft().displayHeight - 1;

        if (Mouse.getEventButton() != -1 && Mouse.isButtonDown(Mouse.getEventButton()) && drawInv) drawInv = false;
        if (popupRect == null || !popupRect.contains(mouseX, mouseY)) return;

        mouseInputEvent.setCanceled(true);

        int relX = mouseX - popupRect.x;
        int relY = mouseY - popupRect.y;


        try {
            PlayerProfile playerProfile = profileFuture.isDone() ? profileFuture.get().orElse(null) : null;
            if (playerProfile == null) return;
            if (Mouse.getEventButton() != -1 && Mouse.isButtonDown(Mouse.getEventButton())) {
                if (new Rectangle(2, 195, 86, 23).contains(relX, relY)) {
                    // invite
                    Minecraft.getMinecraft().thePlayer.sendChatMessage("/p invite " + ApiFetchur.fetchNicknameAsync(playerProfile.getMemberUID()).get().orElse("-"));
                } else if (new Rectangle(2, 170, 86, 23).contains(relX, relY)) {
                    // kick
                    Minecraft.getMinecraft().thePlayer.sendChatMessage("/p kick " + ApiFetchur.fetchNicknameAsync(playerProfile.getMemberUID()).get().orElse("-"));
                } else if (new Rectangle(80,159,10,11).contains(relX, relY)) {
                    drawInv = true;
                }
            }

        } catch (InterruptedException | ExecutionException e) {
        }


    }

    public IChatComponent getHoveredComponent(ScaledResolution scaledResolution) {
        IChatComponent ichatcomponent = null;
        if (Loader.isModLoaded("hychat")) {
            try {
                ChatManager chatManager = HyChat.getInstance().getChatManager();
                GuiChatBox guiChatBox = chatManager.getFocusedChat();

                int x = guiChatBox.getX(scaledResolution);
                int y = guiChatBox.getY(scaledResolution);
                ichatcomponent = guiChatBox.chatArray.getHoveredComponent(guiChatBox.getSelectedTab().getChatLines(), Mouse.getX(), Mouse.getY(), x, y);
            } catch (Throwable t) {}
        }
        if (ichatcomponent == null) {
            ichatcomponent = Minecraft.getMinecraft().ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());
        }
        return ichatcomponent;
    }

    @Override
    public void onChat(ClientChatReceivedEvent clientChatReceivedEvent) {
        if (!isEnabled()) return;
        String str = clientChatReceivedEvent.message.getFormattedText();
        if (str.contains("§r§ejoined the dungeon group! (§r§b")) {
            String username = TextUtils.stripColor(str).split(" ")[3];
            if (username.equalsIgnoreCase(Minecraft.getMinecraft().getSession().getUsername())) {
                Minecraft.getMinecraft().thePlayer.sendChatMessage("/pl");
                PartyManager.INSTANCE.getRunOnMembersReceived().add((e) -> {
                    for (String s : e) {
                        ApiFetchur.fetchUUIDAsync(s)
                                .thenAccept(a -> {
                                    if (a == null) return;
                                    ApiFetchur.fetchMostRecentProfileAsync(a.get(), FeatureRegistry.PARTYKICKER_APIKEY.getAPIKey());
                                    Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§eDungeons Guide §7:: §e"+s+"§f's Profile ").appendSibling(new ChatComponentText("§7view").setChatStyle(new ChatStyle().setChatHoverEvent(new FeatureViewPlayerOnJoin.HoverEventRenderPlayer(a.orElse(null))))));
                                });
                    }
                });
            } else {
                ApiFetchur.fetchUUIDAsync(username)
                        .thenAccept(a -> {
                            if (a == null) return;
                            ApiFetchur.fetchMostRecentProfileAsync(a.get(), FeatureRegistry.PARTYKICKER_APIKEY.getAPIKey());
                            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§eDungeons Guide §7:: §e"+username+"§f's Profile ").appendSibling(new ChatComponentText("§7view").setChatStyle(new ChatStyle().setChatHoverEvent(new FeatureViewPlayerOnJoin.HoverEventRenderPlayer(a.orElse(null))))));
                        });
            }
        }
    }


    public static class HoverEventRenderPlayer extends HoverEvent {
        @Getter
        private String uuid;
        public HoverEventRenderPlayer(String uuid) {
            super(Action.SHOW_TEXT, new ChatComponentText(""));
            this.uuid = uuid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            HoverEventRenderPlayer that = (HoverEventRenderPlayer) o;
            return Objects.equals(uuid, that.uuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), uuid);
        }

        private IChatComponent cached;

        @Override
        public IChatComponent getValue() {
            if (cached == null)
            return cached = new ChatComponentText("").setChatStyle(new ChatStyle().setChatHoverEvent(new HoverEvent(Action.SHOW_TEXT, new ChatComponentText(uuid))));
            return cached;
        }
    }

    public static class FakePlayer extends EntityOtherPlayerMP {
        @Setter
        @Getter
        private PlayerProfile skyblockProfile;
        private SkinFetchur.SkinSet skinSet;
        private PlayerProfile.Armor armor;
        private FakePlayer(World w) {
            super(w, null);
            throw new UnsupportedOperationException("what");
        }
        public FakePlayer(GameProfile playerProfile, SkinFetchur.SkinSet skinSet, PlayerProfile skyblockProfile) {
            super(Minecraft.getMinecraft().theWorld, playerProfile);
            this.skyblockProfile = skyblockProfile;
            this.skinSet = skinSet;
            armor=  skyblockProfile.getCurrentArmor();
            this.inventory.armorInventory = skyblockProfile.getCurrentArmor().getArmorSlots();

            int highestDungeonScore = Integer.MIN_VALUE;
            if (skyblockProfile.getInventory() != null) {
                ItemStack highestItem = null;
                for (ItemStack itemStack : skyblockProfile.getInventory()) {
                    if (itemStack == null) continue;
                    NBTTagCompound display = itemStack.getTagCompound().getCompoundTag("display");
                    if (display == null) continue;
                    NBTTagList nbtTagList = display.getTagList("Lore", 8);
                    if (nbtTagList == null) continue;
                    for (int i = 0; i < nbtTagList.tagCount(); i++) {
                        String str = nbtTagList.getStringTagAt(i);
                        if (TextUtils.stripColor(str).startsWith("Gear")) {
                            System.out.println(str);
                            int dungeonScore = Integer.parseInt(TextUtils.keepIntegerCharactersOnly(TextUtils.stripColor(str).split(" ")[2]));
                            if (dungeonScore > highestDungeonScore) {
                                highestItem = itemStack;
                                highestDungeonScore = dungeonScore;
                            }
                        }
                    }
                }

                this.inventory.mainInventory[0] = highestItem;
                this.inventory.currentItem = 0;
            }
        }

        public String getSkinType() {
            return this.skinSet == null ? DefaultPlayerSkin.getSkinType(getGameProfile().getId()) : this.skinSet.getSkinType();
        }

        public ResourceLocation getLocationSkin() {
            return com.google.common.base.Objects.firstNonNull(skinSet.getSkinLoc(), DefaultPlayerSkin.getDefaultSkin(getGameProfile().getId()));
        }

        public ResourceLocation getLocationCape() {
            return skinSet.getCapeLoc();
        }

        @Override
        public ItemStack[] getInventory() {
            return this.inventory.armorInventory;
        }
    }



    @Override
    public String getEditRoute(final GuiConfig config) {
        ConfigPanelCreator.map.put("base." + getKey() , new Supplier<MPanel>() {
            @Override
            public MPanel get() {
                return new PanelDefaultParameterConfig(config, FeatureViewPlayerOnJoin.this,
                        Arrays.asList(new MPanel[] {
                                new DataRendererEditor(config, FeatureViewPlayerOnJoin.this)
                        }), Collections.singleton("datarenderers"));
            }
        });
        return "base." + getKey() ;
    }
}
