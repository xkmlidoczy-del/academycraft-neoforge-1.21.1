/* AcademyCraft 1.0.7 MetalFormerRecipes and conditional ore-dictionary rules. GPLv3. */
package cn.academy.port.former;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class ClassicMetalFormerRecipes {
    public record Recipe(ClassicMetalFormerWork.Mode mode,ItemStack input,ItemStack output) {
        public boolean accepts(ItemStack stack,ClassicMetalFormerWork.Mode requested){return !stack.isEmpty()&&mode==requested&&stack.getItem()==input.getItem()&&stack.getDamageValue()==input.getDamageValue()&&stack.getCount()>=input.getCount();}
    }
    private ClassicMetalFormerRecipes(){}
    private static Item item(String id){var key=ResourceLocation.parse(id);if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalStateException("Missing genuine Metal Former item "+id);return BuiltInRegistries.ITEM.get(key);}
    public static List<Recipe> builtIns(){var list=new ArrayList<Recipe>();for(var rule:ClassicMetalFormerRules.BUILT_INS)list.add(new Recipe(rule.mode(),new ItemStack(item(rule.input()),rule.inputCount()),new ItemStack(item(rule.output()),rule.outputCount())));return List.copyOf(list);}
    private static List<ItemStack> dictionary(String name){var tag=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c",name));var list=new ArrayList<ItemStack>();BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders->holders.forEach(holder->list.add(new ItemStack(holder.value()))));return list;}
    /** Resolve each scan against current tags/recipes, so reload cannot retain stale compatibility rules. */
    public static List<Recipe> all(Level level){
        var list=new ArrayList<>(builtIns());
        // Source ordinary ore-dictionary aliases: only stone-era vanilla identities are built in.
        // Third-party blocks under these modern c: tags extend the source aliases. Modern vanilla
        // deepslate/nether-gold equivalents are intentionally excluded rather than silently invented.
        String[] names={"gold","iron","emerald","quartz","diamond","redstone","lapis","coal"};
        for(int i=0;i<names.length;i++)for(var ore:dictionary("ores/"+names[i])){
            if(BuiltInRegistries.ITEM.getKey(ore.getItem()).getNamespace().equals("minecraft"))continue;
            var base=list.get(9+i);if(list.stream().noneMatch(recipe->recipe.mode()==ClassicMetalFormerWork.Mode.REFINE&&recipe.input().getItem()==ore.getItem()))list.add(new Recipe(ClassicMetalFormerWork.Mode.REFINE,ore,base.output().copy()));
        }
        if(level!=null)for(var metal:ClassicMetalFormerRules.CONDITIONAL_METALS){
            var output=dictionary("ingots/"+metal);if(output.isEmpty())continue;
            var ores=dictionary("ores/"+metal);if(ores.isEmpty())continue;
            var smelt=level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(ores.getFirst()),level);
            if(smelt.isEmpty())continue;var result=smelt.get().value().assemble(new SingleRecipeInput(ores.getFirst()),level.registryAccess());if(result.isEmpty())continue;
            var product=output.getFirst().copy();product.setCount(ClassicMetalFormerRules.refinedCount(result.getCount()));
            for(var ore:ores)list.add(new Recipe(ClassicMetalFormerWork.Mode.REFINE,ore,product.copy()));
        }
        return List.copyOf(list);
    }
    public static int find(List<Recipe> recipes,ItemStack stack,ClassicMetalFormerWork.Mode mode){for(int i=0;i<recipes.size();i++)if(recipes.get(i).accepts(stack,mode))return i;return -1;}
    public static boolean guiInput(ItemStack stack,Level level){if(stack.isEmpty())return false;return all(level).stream().anyMatch(recipe->recipe.input().getItem()==stack.getItem());}
}
