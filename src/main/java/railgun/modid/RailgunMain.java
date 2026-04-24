package railgun.modid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RailgunMain implements ModInitializer {
    public static final String MOD_ID = "railgun";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String RAILGUN_NBT_KEY = "is_railgun";

    @Override
    public void onInitialize() {
        LOGGER.info("Railgun mod initialized!");

        // 注册射击逻辑（右键使用）
        UseItemCallback.EVENT.register(this::onItemUse);

        // 注册命令来获取railgun
        registerCommand();

    }

    // 注册命令
    private void registerCommand() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("railgun")
                    .requires(source -> {
                        try {
                            var player = source.getPlayer();
                            return player != null && player.isCreative();
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .executes(context -> {
                        try {
                            var player = context.getSource().getPlayer();
                            if (player != null) {
                                player.getInventory().offerOrDrop(createRailgunWithLowDurability());
                                context.getSource().sendFeedback(() -> Text.literal(" "), false);
                            }
                            return 1;
                        } catch (Exception e) {
                            return 0;
                        }
                    }));
        });
    }

    // 射击逻辑
    private ActionResult onItemUse(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        // 检查是否是弩
        if (stack.getItem() != Items.CROSSBOW) {
            return ActionResult.PASS;
        }

        // 检查是否有railgun的NBT标记
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || !customData.copyNbt().contains(RAILGUN_NBT_KEY)) {
            return ActionResult.PASS;
        }

        // 检查是否装填
        ChargedProjectilesComponent charged = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
        if (charged == null || charged.isEmpty()) {
            return ActionResult.PASS;
        }

        if (!world.isClient()) {
            // 射出16支箭
            for (int i = 0; i < 16; i++) {
                ArrowEntity arrow = new ArrowEntity(world, player, new ItemStack(Items.ARROW), stack);
                float speed = 100.0F;
                float divergence = 1.0F;
                arrow.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, speed, divergence);
                arrow.setDamage(2.0);
                arrow.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
                world.spawnEntity(arrow);
            }

            // 播放声音
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F);

            // 清空弹药
            stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT);

            // 创造模式下原版不会掉耐久，这里直接销毁 Railgun，保证也是一次性。
            if (player.isCreative()) {
                player.setStackInHand(hand, ItemStack.EMPTY);
            } else {
                stack.damage(1, player, EquipmentSlot.MAINHAND);
                if (stack.getDamage() >= stack.getMaxDamage()) {
                    stack.decrement(1);
                }
            }
        }

        return ActionResult.SUCCESS;
    }

    // 创建Railgun（满耐久）
    public static ItemStack createRailgun() {
        ItemStack stack = new ItemStack(Items.CROSSBOW);

        // 设置自定义名称
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Railgun"));

        // 设置NBT标记
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(RAILGUN_NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        // 预设装填
        stack.set(DataComponentTypes.CHARGED_PROJECTILES,
                ChargedProjectilesComponent.of(new ItemStack(Items.ARROW)));

        return stack;
    }

    // 创建Railgun（低耐久，用一次就碎）
    public static ItemStack createRailgunWithLowDurability() {
        ItemStack stack = createRailgun();
        // 原版弩最大耐久465，设置成464后只剩1点耐久
        stack.setDamage(464);
        return stack;
    }
}