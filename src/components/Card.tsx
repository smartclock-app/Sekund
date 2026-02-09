interface CardProps {
  padding?: boolean;
  margin?: boolean;
  children?: React.ReactNode;
}

const Card: React.FC<CardProps> = ({ children, padding = true, margin = true }) => {
  return (
    <div
      style={{
        backgroundColor: "#FFF8F8F8",
        borderRadius: "0.6rem",
        padding: padding ? "1rem" : undefined,
        marginBottom: margin ? "1rem" : undefined,
      }}
    >
      {children}
    </div>
  );
};

export default Card;
