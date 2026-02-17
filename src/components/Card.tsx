interface CardProps {
  padding?: boolean;
  children?: React.ReactNode;
}

const Card: React.FC<CardProps> = ({ children, padding = true }) => {
  return (
    <div
      style={{
        backgroundColor: "rgb(236, 236, 236)",
        borderRadius: "var(--card-radius, 0.6rem)",
        padding: padding ? "var(--card-padding, 1rem)" : undefined,
      }}
    >
      {children}
    </div>
  );
};

export default Card;
