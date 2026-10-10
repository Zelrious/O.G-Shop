import React, { createContext, useContext, useState, useEffect } from 'react';

export interface CartItem {
  productId: number;
  title: string;
  price: number;
  thumbnailUrl?: string | null;
  condition: string;
  sellerId?: number;
  sellerName?: string;
  location?: string | null;
  addedAt: string;
}

interface CartContextValue {
  items: CartItem[];
  totalCount: number;
  totalAmount: number;
  addToCart: (item: Omit<CartItem, 'addedAt'>) => { success: boolean; message: string };
  removeFromCart: (productId: number) => void;
  clearCart: () => void;
  isInCart: (productId: number) => boolean;
}

const CartContext = createContext<CartContextValue | undefined>(undefined);

const STORAGE_KEY = 'og_cart_items';

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [items, setItems] = useState<CartItem[]>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (err) {
      console.warn('Lỗi khi đọc giỏ hàng từ localStorage:', err);
    }
    return [];
  });

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch (err) {
      console.warn('Lỗi khi lưu giỏ hàng vào localStorage:', err);
    }
  }, [items]);

  const isInCart = (productId: number) => items.some((item) => item.productId === productId);

  const addToCart = (item: Omit<CartItem, 'addedAt'>) => {
    if (isInCart(item.productId)) {
      return {
        success: false,
        message: 'Món đồ này đã có trong giỏ hàng của bạn.',
      };
    }

    const newItem: CartItem = {
      ...item,
      addedAt: new Date().toISOString(),
    };

    setItems((prev) => [newItem, ...prev]);
    return {
      success: true,
      message: 'Đã thêm món đồ vào giỏ hàng thành công!',
    };
  };

  const removeFromCart = (productId: number) => {
    setItems((prev) => prev.filter((item) => item.productId !== productId));
  };

  const clearCart = () => {
    setItems([]);
  };

  const totalCount = items.length;
  const totalAmount = items.reduce((sum, item) => sum + (item.price || 0), 0);

  return (
    <CartContext.Provider
      value={{
        items,
        totalCount,
        totalAmount,
        addToCart,
        removeFromCart,
        clearCart,
        isInCart,
      }}
    >
      {children}
    </CartContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useCart = (): CartContextValue => {
  const context = useContext(CartContext);
  if (!context) {
    // Return graceful fallback if rendered outside CartProvider (e.g. in standalone tests)
    return {
      items: [],
      totalCount: 0,
      totalAmount: 0,
      addToCart: () => ({ success: false, message: 'CartProvider missing' }),
      removeFromCart: () => {},
      clearCart: () => {},
      isInCart: () => false,
    };
  }
  return context;
};
