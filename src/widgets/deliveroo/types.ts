export interface Order {
  id: string;
  drn_id: string;
  drn: string;
  order_type: string;
  order_number: number;
  total: string;
  subtotal: string;
  fee: string;
  delivery_fee: string;
  tip: string;
  card_fee: number;
  credit_used: string;
  corporate_allowance_used: string;
  balance: string;
  currency_symbol: string;
  currency_code: string;
  submitted_at: string;
  advance_order: boolean;
  estimated_delivery_at: string;
  delivered_at: string;
  needs_rating: boolean;
  status: string;
  status_timestamp: string;
  consumer_status: {
    code: string;
  };
  restaurant: {
    id: string;
    name: string;
    category: string;
    image_url: string;
    coordinates: [number, number];
    branch_type: string;
  };
  address: {
    id: string;
    label: string;
    address1: string;
    address2: string;
    phone: string;
    post_code: string;
    city: string;
    coordinates: [number, number];
  };
  items: {
    name: string;
    quantity: number;
    unit_price: string;
    total_unit_price: string;
    modifiers: {
      name: string;
      omit_from_receipts: boolean;
    }[];
    discount_amount: string;
    original_item: null;
    refactors_item: null;
    id: string;
    drn_id: string;
    requested_quantity: number;
  }[];
  archived_items: [];
  drivers: [];
  ugc_review: null;
  reviewable: boolean;
  should_display_rating: boolean;
  fee_breakdown: [];
  payment_breakdown: [];
  zone: null;
}

interface OrderStatusRelationship {
  data: {
    id: string;
    drn_id: string;
    type: string;
  };
}

export interface OrderStatus {
  data: {
    type: "consumer_order_status";
    id: string;
    attributes: {
      title: string;
      message: string;
      emphasize_advisory: boolean;
      fulfillment_type: string;
      ui_status: string;
      is_failed: boolean;
      is_completed: boolean;
      redirect_to_order_history: boolean;
      show_live_indicator: boolean;
      show_share_order_tracker_link: boolean;
      show_map: boolean;
      can_show_rate_app: boolean;
      can_show_rewards_progress: boolean;
      updated_at: string;
      updated_at_formatted: string;
      colour_scheme: string;
      are_processing_steps_expandable: boolean;
      header_display_state: string;
      sheet_display_state: string;
      emphasize_title: boolean;
      authenticated_via: string;
      rider_validation_code: number;
      rider_validation_code_s: string;
      rider_validation_code_title: string;
      rider_validation_code_subtitle: string;
      delivery_header: string;
      partner_phone_number: string;
      feedback: {
        feedback_question: string;
        type: string;
      };
      order_summary_collapsible: boolean;
      live_activity: string;
      completed_order_celebration: boolean;
      branch_type: string;
      customer_facing_link: string;
      variable_weight_pre_auth_enabled: boolean;
    };
    relationships: {
      order: OrderStatusRelationship;
      banner: OrderStatusRelationship;
      riders: {
        data: [];
      };
      retry_payment_links: OrderStatusRelationship;
      post_order_tipping_banner: OrderStatusRelationship;
      customer: OrderStatusRelationship;
      scrollable_header: OrderStatusRelationship;
    };
  };
  included: [
    {
      id: string;
      drn_id: string;
      type: string;
      attributes: {
        order_number: string;
        short_drn: string;
        sharing_enabled: boolean;
        customer_name: string;
        address: string;
        country: string;
        description: string;
        description_drn: string;
        image_url: string;
        items: string;
        full_items: {
          id: string;
          name: string;
          modifiers_description: string;
          quantity: number;
          requested_quantity: number;
          available_quantity: number;
          total_price: string;
          final_quantity: number;
          modifiers: {
            name: string;
            requested_quantity: number;
            final_quantity: number;
          }[];
        }[];
        show_modifiers_in_new_lines: boolean;
        restaurant_name: string;
        restaurant_drn_id: string;
        restaurant_id: number;
        price_details: {
          iso_currency_code: string;
          fractional_amount: number;
        };
        totals: {
          label: string;
          type: string;
          amount: string;
        }[];
        placed_at_message: string;
        title: string;
        first_presented_eta_lower_bound: {
          "^t": number;
        };
        first_presented_eta_upper_bound: {
          "^t": number;
        };
        delivery_prioritisation: string;
      };
    },
    {
      id: string;
      drn_id: string;
      type: string;
      attributes: {
        short_message: string;
        show_detail_indicator: boolean;
        short_message_subtitle: string;
      };
    },
    {
      id: string;
      drn_id: string;
      type: string;
      attributes: {
        collapsed_title: string;
        collapsed_subtitle: string;
      };
    },
  ];
}
