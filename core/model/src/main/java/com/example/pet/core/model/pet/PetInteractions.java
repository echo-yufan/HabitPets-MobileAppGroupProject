package com.example.pet.core.model.pet;

/** E's synchronous contract. Call on a worker thread; request IDs are stable across retries. */
public interface PetInteractions {
    enum Status { SUCCESS, NO_STOCK, FULL, ALREADY_EQUIPPED, INVALID_ITEM, INVALID_REQUEST, PENDING, STORAGE_ERROR }
    final class Result {
        public final Status status;
        public final String itemId;
        public final boolean food;
        public Result(Status status, String itemId, boolean food) {
            this.status = status; this.itemId = itemId; this.food = food;
        }
    }
    Result useItem(String itemId, String requestId);
    Result unequip(String requestId);
    String getEquippedItemId();
    default java.util.List<String> getEquippedItemIds() {
        String id=getEquippedItemId();
        return id == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(id);
    }
    default Result unequipSlot(String slot, String requestId) { return unequip(requestId); }
    default java.util.List<PetCareItem> getCareItems() { return java.util.Collections.emptyList(); }
    default int getCoinBalance() { return 0; }
    int[] getFoodEffect(String itemId);
    void recover();
}
